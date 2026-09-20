package com.deliveryweb.dao;

import com.deliveryweb.pojo.Delivery;
import com.deliveryweb.pojo.DeliveryVO;
import com.deliveryweb.util.Constants;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 配送记录数据访问接口（MyBatis Mapper，注解实现）。
 * 一单一记录（uk_order_id）。
 * 抢单与履约节点一律条件更新（WHERE 带当前状态），以影响行数判断并发/跳步。
 */
public interface DeliveryDao {

    String BASE_COLUMNS = "id, order_id, rider_id, status, accepted_at, picked_at, delivered_at, remark, updated_at";

    /** 池/本人列表/详情共用的联表列：配送记录 + 订单/店铺关键列（下划线别名经驼峰映射入 DeliveryVO） */
    String VO_COLUMNS = "d.id AS delivery_id, d.order_id, o.order_no, o.order_status, o.total_amount, o.pay_amount, "
            + "s.name AS shop_name, s.address AS shop_address, o.address_snapshot, "
            + "d.rider_id, d.status, d.accepted_at, d.picked_at, d.delivered_at, d.remark, d.updated_at";

    String VO_JOIN = " FROM delivery d JOIN orders o ON o.id = d.order_id JOIN shop s ON s.id = o.shop_id ";

    /** 备餐完成时创建配送记录：骑手为空、状态待抢单，进入配送池 */
    @Insert("INSERT INTO delivery (order_id, rider_id, status, remark, updated_at) "
            + "VALUES (#{orderId}, #{riderId}, #{status}, #{remark}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Delivery delivery);

    @Select("SELECT " + BASE_COLUMNS + " FROM delivery WHERE order_id = #{orderId}")
    Delivery findByOrder(@Param("orderId") Long orderId);

    @Select("SELECT " + BASE_COLUMNS + " FROM delivery WHERE id = #{id}")
    Delivery findById(@Param("id") Long id);

    /** 配送池分页：仅待抢单记录，早入池排前 */
    @Select("SELECT " + VO_COLUMNS + VO_JOIN + "WHERE d.status = #{status} ORDER BY d.id LIMIT #{offset}, #{size}")
    List<DeliveryVO> poolList(@Param("status") String status, @Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM delivery d WHERE d.status = #{status}")
    long countPool(@Param("status") String status);

    /** 本人履约记录分页：状态可选筛选；进行中优先（配送中→已接单→待抢→已送达），同状态按更新时间新到老 */
    @Select("<script>"
            + "SELECT " + VO_COLUMNS + VO_JOIN
            + "WHERE d.rider_id = #{riderId} "
            + "<if test=\"status != null and status != ''\">AND d.status = #{status} </if>"
            + "ORDER BY FIELD(d.status, '" + Constants.DELIVERY_DELIVERING + "', '" + Constants.DELIVERY_ACCEPTED + "', '" + Constants.DELIVERY_PENDING + "', '" + Constants.DELIVERY_DELIVERED + "'), d.updated_at DESC "
            + "LIMIT #{offset}, #{size}"
            + "</script>")
    List<DeliveryVO> myList(@Param("riderId") Long riderId, @Param("status") String status,
                            @Param("offset") int offset, @Param("size") int size);

    @Select("<script>"
            + "SELECT COUNT(*) FROM delivery d "
            + "WHERE d.rider_id = #{riderId} "
            + "<if test=\"status != null and status != ''\">AND d.status = #{status} </if>"
            + "</script>")
    long countMy(@Param("riderId") Long riderId, @Param("status") String status);

    @Select("SELECT " + VO_COLUMNS + VO_JOIN + "WHERE d.order_id = #{orderId}")
    DeliveryVO findVoByOrder(@Param("orderId") Long orderId);

    /** 抢单条件更新：仅待抢单记录可写入骑手，并发仅一人影响行数为 1 */
    @Update("UPDATE delivery SET rider_id = #{riderId}, status = #{targetStatus}, "
            + "accepted_at = #{acceptedAt}, updated_at = #{updatedAt} "
            + "WHERE id = #{id} AND status = #{currentStatus}")
    int grab(@Param("id") Long id,
             @Param("riderId") Long riderId,
             @Param("currentStatus") String currentStatus,
             @Param("targetStatus") String targetStatus,
             @Param("acceptedAt") LocalDateTime acceptedAt,
             @Param("updatedAt") LocalDateTime updatedAt);

    /** 履约节点条件更新：仅当前状态匹配才生效；取餐/送达时间戳按需写入 */
    @Update("<script>"
            + "UPDATE delivery SET status = #{targetStatus}, updated_at = #{updatedAt} "
            + "<if test=\"pickedAt != null\">, picked_at = #{pickedAt} </if>"
            + "<if test=\"deliveredAt != null\">, delivered_at = #{deliveredAt} </if>"
            + "WHERE id = #{id} AND status = #{currentStatus}"
            + "</script>")
    int transferNode(@Param("id") Long id,
                     @Param("currentStatus") String currentStatus,
                     @Param("targetStatus") String targetStatus,
                     @Param("pickedAt") LocalDateTime pickedAt,
                     @Param("deliveredAt") LocalDateTime deliveredAt,
                     @Param("updatedAt") LocalDateTime updatedAt);

    /** 配送说明/异常反馈：仅本人承接的配送可写 */
    @Update("UPDATE delivery SET remark = #{remark}, updated_at = #{updatedAt} "
            + "WHERE id = #{id} AND rider_id = #{riderId}")
    int updateRemark(@Param("id") Long id,
                     @Param("riderId") Long riderId,
                     @Param("remark") String remark,
                     @Param("updatedAt") LocalDateTime updatedAt);

    /** 骑手战绩：今日送达单数（delivered_at 落在当天） */
    @Select("SELECT COUNT(*) FROM delivery WHERE rider_id = #{riderId} AND status = '" + Constants.DELIVERY_DELIVERED + "' AND delivered_at >= CURDATE()")
    long countTodayDelivered(@Param("riderId") Long riderId);

    /** 骑手战绩：在途单数（已接单未取餐 + 配送中） */
    @Select("SELECT COUNT(*) FROM delivery WHERE rider_id = #{riderId} AND status IN ('" + Constants.DELIVERY_ACCEPTED + "', '" + Constants.DELIVERY_DELIVERING + "')")
    long countActive(@Param("riderId") Long riderId);

    /** 骑手战绩：累计送达单数 */
    @Select("SELECT COUNT(*) FROM delivery WHERE rider_id = #{riderId} AND status = '" + Constants.DELIVERY_DELIVERED + "'")
    long countDeliveredTotal(@Param("riderId") Long riderId);
}
