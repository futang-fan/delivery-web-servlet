package com.deliveryweb.dao;

import com.deliveryweb.pojo.Order;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单主表数据访问接口（MyBatis Mapper，注解实现）。
 * 状态变更一律走条件更新（WHERE 带当前状态），以影响行数判断并发/重复操作。
 */
public interface OrderDao {

    String BASE_COLUMNS = "id, order_no, customer_id, shop_id, address_snapshot, total_amount, pay_amount, "
            + "pay_status, paid_at, accepted_at, prepared_at, order_status, remark, created_at, updated_at";

    @Insert("INSERT INTO orders (order_no, customer_id, shop_id, address_snapshot, total_amount, pay_amount, "
            + "pay_status, order_status, remark, created_at, updated_at) "
            + "VALUES (#{orderNo}, #{customerId}, #{shopId}, #{addressSnapshot}, #{totalAmount}, #{payAmount}, "
            + "#{payStatus}, #{orderStatus}, #{remark}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Order order);

    @Select("SELECT " + BASE_COLUMNS + " FROM orders WHERE id = #{id}")
    Order findById(@Param("id") Long id);

    @Select("SELECT " + BASE_COLUMNS + " FROM orders WHERE id = #{id} AND customer_id = #{customerId}")
    Order findOwned(@Param("id") Long id, @Param("customerId") Long customerId);

    /** 通用状态流转条件更新：仅当前状态匹配才生效，返回影响行数（接单/拒单/备餐/取消共用） */
    @Update("UPDATE orders SET order_status = #{targetStatus}, updated_at = #{updatedAt} "
            + "WHERE id = #{id} AND order_status = #{currentStatus}")
    int transferStatus(@Param("id") Long id,
                       @Param("currentStatus") String currentStatus,
                       @Param("targetStatus") String targetStatus,
                       @Param("updatedAt") LocalDateTime updatedAt);

    /** 接单专用条件更新：流转 PENDING_ACCEPT→PREPARING 同时写 accepted_at；不复用 transferStatus 以免波及其余调用方 */
    @Update("UPDATE orders SET order_status = #{targetStatus}, accepted_at = #{nodeAt}, updated_at = #{updatedAt} "
            + "WHERE id = #{id} AND order_status = #{currentStatus}")
    int acceptWithTime(@Param("id") Long id,
                       @Param("currentStatus") String currentStatus,
                       @Param("targetStatus") String targetStatus,
                       @Param("nodeAt") LocalDateTime nodeAt,
                       @Param("updatedAt") LocalDateTime updatedAt);

    /** 备餐完成专用条件更新：流转 PREPARING→PENDING_DELIVERY 同时写 prepared_at */
    @Update("UPDATE orders SET order_status = #{targetStatus}, prepared_at = #{nodeAt}, updated_at = #{updatedAt} "
            + "WHERE id = #{id} AND order_status = #{currentStatus}")
    int readyWithTime(@Param("id") Long id,
                      @Param("currentStatus") String currentStatus,
                      @Param("targetStatus") String targetStatus,
                      @Param("nodeAt") LocalDateTime nodeAt,
                      @Param("updatedAt") LocalDateTime updatedAt);

    /** 模拟支付条件更新：仅待支付订单可支付，同时推进支付状态、订单状态并记录支付时间 */
    @Update("UPDATE orders SET pay_status = #{payStatus}, paid_at = #{paidAt}, order_status = #{targetStatus}, "
            + "updated_at = #{updatedAt} WHERE id = #{id} AND order_status = #{currentStatus}")
    int pay(@Param("id") Long id,
            @Param("currentStatus") String currentStatus,
            @Param("targetStatus") String targetStatus,
            @Param("payStatus") String payStatus,
            @Param("paidAt") LocalDateTime paidAt,
            @Param("updatedAt") LocalDateTime updatedAt);

    /** 退款同意时支付状态条件更新：仅已支付订单可置已退款，防重复计入交易统计 */
    @Update("UPDATE orders SET pay_status = #{payStatus}, updated_at = #{updatedAt} "
            + "WHERE id = #{id} AND pay_status = #{currentPayStatus}")
    int refundPay(@Param("id") Long id,
                  @Param("currentPayStatus") String currentPayStatus,
                  @Param("payStatus") String payStatus,
                  @Param("updatedAt") LocalDateTime updatedAt);

    /** 超时未支付扫描：创建时间早于截止时间的待支付订单 */
    @Select("SELECT " + BASE_COLUMNS + " FROM orders "
            + "WHERE pay_status = #{payStatus} AND order_status = #{orderStatus} AND created_at < #{cutoff} "
            + "LIMIT 200")
    List<Order> listExpiredUnpaid(@Param("payStatus") String payStatus,
                                  @Param("orderStatus") String orderStatus,
                                  @Param("cutoff") LocalDateTime cutoff);

    /** 已送达超时扫描：DELIVERED 且配送记录 delivered_at 早于截止时间的订单（OrderTimeoutListener 第二任务共用） */
    @Select("SELECT o.id, o.order_no, o.customer_id, o.shop_id, o.address_snapshot, o.total_amount, o.pay_amount, "
            + "o.pay_status, o.paid_at, o.accepted_at, o.prepared_at, o.order_status, o.remark, o.created_at, o.updated_at "
            + "FROM orders o JOIN delivery d ON d.order_id = o.id "
            + "WHERE o.order_status = #{orderStatus} AND d.delivered_at < #{cutoff}")
    List<Order> listExpiredDelivered(@Param("orderStatus") String orderStatus,
                                     @Param("cutoff") LocalDateTime cutoff);

    /** 管理员订单监管分页：订单号/用户名/店名/日期区间/状态动态条件，联表取店名与顾客名 */
    @Select("<script>"
            + "SELECT o.id, o.order_no, o.customer_id, o.shop_id, o.address_snapshot, o.total_amount, o.pay_amount, "
            + "o.pay_status, o.paid_at, o.accepted_at, o.prepared_at, o.order_status, o.remark, o.created_at, o.updated_at, "
            + "s.name AS shop_name, u.username AS customer_name "
            + "FROM orders o JOIN shop s ON s.id = o.shop_id JOIN user u ON u.id = o.customer_id "
            + "<where>"
            + "<if test=\"orderNo != null and orderNo != ''\">AND o.order_no LIKE CONCAT('%', #{orderNo}, '%') </if>"
            + "<if test=\"username != null and username != ''\">AND u.username LIKE CONCAT('%', #{username}, '%') </if>"
            + "<if test=\"shopName != null and shopName != ''\">AND s.name LIKE CONCAT('%', #{shopName}, '%') </if>"
            + "<if test=\"startDate != null and startDate != ''\">AND o.created_at &gt;= #{startDate} </if>"
            + "<if test=\"endDate != null and endDate != ''\">AND o.created_at &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY) </if>"
            + "<if test=\"orderStatus != null and orderStatus != ''\">AND o.order_status = #{orderStatus} </if>"
            + "</where>"
            + "ORDER BY o.id DESC LIMIT #{offset}, #{size}"
            + "</script>")
    List<Order> listForAdmin(@Param("orderNo") String orderNo,
                             @Param("username") String username,
                             @Param("shopName") String shopName,
                             @Param("startDate") String startDate,
                             @Param("endDate") String endDate,
                             @Param("orderStatus") String orderStatus,
                             @Param("offset") int offset,
                             @Param("size") int size);

    @Select("<script>"
            + "SELECT COUNT(*) FROM orders o JOIN shop s ON s.id = o.shop_id JOIN user u ON u.id = o.customer_id "
            + "<where>"
            + "<if test=\"orderNo != null and orderNo != ''\">AND o.order_no LIKE CONCAT('%', #{orderNo}, '%') </if>"
            + "<if test=\"username != null and username != ''\">AND u.username LIKE CONCAT('%', #{username}, '%') </if>"
            + "<if test=\"shopName != null and shopName != ''\">AND s.name LIKE CONCAT('%', #{shopName}, '%') </if>"
            + "<if test=\"startDate != null and startDate != ''\">AND o.created_at &gt;= #{startDate} </if>"
            + "<if test=\"endDate != null and endDate != ''\">AND o.created_at &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY) </if>"
            + "<if test=\"orderStatus != null and orderStatus != ''\">AND o.order_status = #{orderStatus} </if>"
            + "</where>"
            + "</script>")
    long countForAdmin(@Param("orderNo") String orderNo,
                       @Param("username") String username,
                       @Param("shopName") String shopName,
                       @Param("startDate") String startDate,
                       @Param("endDate") String endDate,
                       @Param("orderStatus") String orderStatus);

    /** 四角色分页查询：三个归属参数互斥传入（顾客本人/商家本店/骑手承接），管理员全传 null；联表店铺取店名，
 *  退款状态用标量子查询取最新一条 */
    @Select("<script>"
            + "SELECT o.id, o.order_no, o.customer_id, o.shop_id, o.address_snapshot, o.total_amount, o.pay_amount, "
            + "o.pay_status, o.paid_at, o.accepted_at, o.prepared_at, o.order_status, o.remark, o.created_at, o.updated_at, s.name AS shop_name, "
            + "(SELECT r.status FROM refund r WHERE r.order_id = o.id ORDER BY r.id LIMIT 1) AS refund_status, "
            + "(SELECT GROUP_CONCAT(CONCAT(oi.dish_name_snapshot, 'x', oi.quantity) SEPARATOR '、') "
            + "FROM order_item oi WHERE oi.order_id = o.id) AS dish_summary "
            + "FROM orders o JOIN shop s ON s.id = o.shop_id "
            + "<where>"
            + "<if test=\"customerId != null\">AND o.customer_id = #{customerId} </if>"
            + "<if test=\"shopId != null\">AND o.shop_id = #{shopId} </if>"
            + "<if test=\"riderId != null\">AND o.id IN (SELECT order_id FROM delivery WHERE rider_id = #{riderId}) </if>"
            + "<if test=\"orderStatus != null and orderStatus != ''\">AND o.order_status = #{orderStatus} </if>"
            + "<if test=\"payStatus != null and payStatus != ''\">AND o.pay_status = #{payStatus} </if>"
            + "</where>"
            + "ORDER BY o.id DESC LIMIT #{offset}, #{size}"
            + "</script>")
    List<Order> listByScope(@Param("customerId") Long customerId,
                            @Param("shopId") Long shopId,
                            @Param("riderId") Long riderId,
                            @Param("orderStatus") String orderStatus,
                            @Param("payStatus") String payStatus,
                            @Param("offset") int offset,
                            @Param("size") int size);

    @Select("<script>"
            + "SELECT COUNT(*) FROM orders "
            + "<where>"
            + "<if test=\"customerId != null\">AND customer_id = #{customerId} </if>"
            + "<if test=\"shopId != null\">AND shop_id = #{shopId} </if>"
            + "<if test=\"riderId != null\">AND id IN (SELECT order_id FROM delivery WHERE rider_id = #{riderId}) </if>"
            + "<if test=\"orderStatus != null and orderStatus != ''\">AND order_status = #{orderStatus} </if>"
            + "<if test=\"payStatus != null and payStatus != ''\">AND pay_status = #{payStatus} </if>"
            + "</where>"
            + "</script>")
    long countByScope(@Param("customerId") Long customerId,
                      @Param("shopId") Long shopId,
                      @Param("riderId") Long riderId,
                      @Param("orderStatus") String orderStatus,
                      @Param("payStatus") String payStatus);
}
