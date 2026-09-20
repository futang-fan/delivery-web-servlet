package com.deliveryweb.dao;

import com.deliveryweb.pojo.Refund;
import com.deliveryweb.pojo.RefundVO;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 退款记录数据访问接口（MyBatis Mapper，注解实现）。
 * 三态状态流转在 RefundService 维护。
 * 商家处理走条件更新（WHERE 带当前状态），防重复处理。
 */
public interface RefundDao {

    String BASE_COLUMNS = "id, order_id, reason, status, remark, admin_remark, created_at";

    @Insert("INSERT INTO refund (order_id, reason, status, remark, created_at) "
            + "VALUES (#{orderId}, #{reason}, #{status}, #{remark}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Refund refund);

    /** 按订单查退款记录（一单至多一条有效记录，重复申请校验用） */
    @Select("SELECT " + BASE_COLUMNS + " FROM refund WHERE order_id = #{orderId} ORDER BY id LIMIT 1")
    Refund findByOrder(@Param("orderId") Long orderId);

    @Select("SELECT " + BASE_COLUMNS + " FROM refund WHERE id = #{id}")
    Refund findById(@Param("id") Long id);

    /** 商家处理条件更新：仅待处理记录可流转，同意/拒绝共用，影响行数 0 即已被处理 */
    @Update("UPDATE refund SET status = #{targetStatus}, remark = #{remark} "
            + "WHERE id = #{id} AND status = #{currentStatus}")
    int handle(@Param("id") Long id,
               @Param("currentStatus") String currentStatus,
               @Param("targetStatus") String targetStatus,
               @Param("remark") String remark);

    /** 拒单收敛条件更新：
     *  顾客申请(PENDING)、曾被商家驳回(SHOP_REJECTED)、曾被平台裁定驳回(ADMIN_REJECTED)
     *  的记录统一收敛为 SHOP_AGREED（拒单即同意退款）；已同意态(SHOP_AGREED/ADMIN_AGREED)不影响行数 0 */
    @Update("UPDATE refund SET status = #{targetStatus}, remark = #{remark} "
            + "WHERE id = #{id} AND status IN (#{fromPending}, #{fromRejected}, #{fromAdminRejected})")
    int collapseOnReject(@Param("id") Long id,
                         @Param("targetStatus") String targetStatus,
                         @Param("remark") String remark,
                         @Param("fromPending") String fromPending,
                         @Param("fromRejected") String fromRejected,
                         @Param("fromAdminRejected") String fromAdminRejected);

    /** 本店待处理退款分页：联表订单取订单号与实付金额，早申请排前 */
    @Select("SELECT r.id AS refund_id, r.order_id, o.order_no, o.pay_amount, r.reason, r.status, r.remark, r.created_at "
            + "FROM refund r JOIN orders o ON o.id = r.order_id "
            + "WHERE r.status = #{status} AND o.shop_id = #{shopId} "
            + "ORDER BY r.id LIMIT #{offset}, #{size}")
    List<RefundVO> pendingList(@Param("shopId") Long shopId, @Param("status") String status,
                               @Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM refund r JOIN orders o ON o.id = r.order_id "
            + "WHERE r.status = #{status} AND o.shop_id = #{shopId}")
    long countPending(@Param("shopId") Long shopId, @Param("status") String status);

    /** 平台裁定条件更新（退款仲裁轻量版）：仅待处理记录可流转，同意/驳回共用，同事务写 admin_remark；不改动商家 handle */
    @Update("UPDATE refund SET status = #{targetStatus}, admin_remark = #{adminRemark} "
            + "WHERE id = #{id} AND status = #{currentStatus}")
    int adminDecide(@Param("id") Long id,
                    @Param("currentStatus") String currentStatus,
                    @Param("targetStatus") String targetStatus,
                    @Param("adminRemark") String adminRemark);

    /** 全平台待处理退款分页（管理员仲裁列表）：联表订单/店铺/顾客，早申请排前 */
    @Select("SELECT r.id AS refund_id, r.order_id, o.order_no, o.pay_amount, r.reason, r.status, r.remark, "
            + "r.admin_remark, s.name AS shop_name, u.username AS customer_name, r.created_at "
            + "FROM refund r JOIN orders o ON o.id = r.order_id "
            + "JOIN shop s ON s.id = o.shop_id JOIN user u ON u.id = o.customer_id "
            + "WHERE r.status = #{status} ORDER BY r.id LIMIT #{offset}, #{size}")
    List<RefundVO> pendingListForAdmin(@Param("status") String status,
                                       @Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM refund r WHERE r.status = #{status}")
    long countPendingForAdmin(@Param("status") String status);
}
