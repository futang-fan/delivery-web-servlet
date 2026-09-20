package com.deliveryweb.dao;

import com.deliveryweb.util.Constants;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 统计聚合数据访问接口（MyBatis Mapper，注解实现。
 * 全部为纯聚合 SQL：订单总数与交易金额取已支付且未退款口径（pay_status='" + Constants.PAY_PAID + "'，
 * 同意退款后置 REFUNDED 自然排除）；店铺销量按交易额、菜品销售按销量件数。
 */
public interface StatDao {

    /** 交易订单数（已支付且未退款）；日期可选，含结束日整天，空值即全量 */
    @Select("<script>"
            + "SELECT COUNT(*) FROM orders WHERE pay_status = #{payStatus} "
            + "<if test=\"startDate != null and startDate != ''\">AND created_at &gt;= #{startDate} </if>"
            + "<if test=\"endDate != null and endDate != ''\">AND created_at &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY) </if>"
            + "</script>")
    long countPaidOrders(@Param("payStatus") String payStatus,
                         @Param("startDate") String startDate,
                         @Param("endDate") String endDate);

    /** 交易金额合计（已支付且未退款）；日期可选，含结束日整天，空值即全量 */
    @Select("<script>"
            + "SELECT COALESCE(SUM(pay_amount), 0) FROM orders WHERE pay_status = #{payStatus} "
            + "<if test=\"startDate != null and startDate != ''\">AND created_at &gt;= #{startDate} </if>"
            + "<if test=\"endDate != null and endDate != ''\">AND created_at &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY) </if>"
            + "</script>")
    BigDecimal sumPaidAmount(@Param("payStatus") String payStatus,
                             @Param("startDate") String startDate,
                             @Param("endDate") String endDate);

    /** 店铺销量排行：按已支付订单交易额降序；日期可选 */
    @Select("<script>"
            + "SELECT s.name AS name, COALESCE(SUM(o.pay_amount), 0) AS sales "
            + "FROM orders o JOIN shop s ON s.id = o.shop_id "
            + "WHERE o.pay_status = #{payStatus} "
            + "<if test=\"startDate != null and startDate != ''\">AND o.created_at &gt;= #{startDate} </if>"
            + "<if test=\"endDate != null and endDate != ''\">AND o.created_at &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY) </if>"
            + "GROUP BY s.id, s.name ORDER BY sales DESC LIMIT #{limit}"
            + "</script>")
    List<Map<String, Object>> shopRank(@Param("payStatus") String payStatus,
                                       @Param("startDate") String startDate,
                                       @Param("endDate") String endDate,
                                       @Param("limit") int limit);

    /** 菜品销售排行：按已支付订单中的销量件数降序；日期可选 */
    @Select("<script>"
            + "SELECT d.name AS name, COALESCE(SUM(oi.quantity), 0) AS sales "
            + "FROM order_item oi JOIN orders o ON o.id = oi.order_id JOIN dish d ON d.id = oi.dish_id "
            + "WHERE o.pay_status = #{payStatus} "
            + "<if test=\"startDate != null and startDate != ''\">AND o.created_at &gt;= #{startDate} </if>"
            + "<if test=\"endDate != null and endDate != ''\">AND o.created_at &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY) </if>"
            + "GROUP BY d.id, d.name ORDER BY sales DESC LIMIT #{limit}"
            + "</script>")
    List<Map<String, Object>> dishRank(@Param("payStatus") String payStatus,
                                       @Param("startDate") String startDate,
                                       @Param("endDate") String endDate,
                                       @Param("limit") int limit);

    /** 店铺销量排行：按已支付订单中的销量件数降序；日期可选 */
    @Select("<script>"
            + "SELECT s.name AS name, COALESCE(SUM(oi.quantity), 0) AS sales "
            + "FROM order_item oi JOIN orders o ON o.id = oi.order_id JOIN shop s ON s.id = o.shop_id "
            + "WHERE o.pay_status = #{payStatus} "
            + "<if test=\"startDate != null and startDate != ''\">AND o.created_at &gt;= #{startDate} </if>"
            + "<if test=\"endDate != null and endDate != ''\">AND o.created_at &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY) </if>"
            + "GROUP BY s.id, s.name ORDER BY sales DESC LIMIT #{limit}"
            + "</script>")
    List<Map<String, Object>> shopQuantityRank(@Param("payStatus") String payStatus,
                                               @Param("startDate") String startDate,
                                               @Param("endDate") String endDate,
                                               @Param("limit") int limit);

    /** 菜品销售排行：按已支付订单明细小计金额降序；日期可选 */
    @Select("<script>"
            + "SELECT d.name AS name, COALESCE(SUM(oi.subtotal), 0) AS sales "
            + "FROM order_item oi JOIN orders o ON o.id = oi.order_id JOIN dish d ON d.id = oi.dish_id "
            + "WHERE o.pay_status = #{payStatus} "
            + "<if test=\"startDate != null and startDate != ''\">AND o.created_at &gt;= #{startDate} </if>"
            + "<if test=\"endDate != null and endDate != ''\">AND o.created_at &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY) </if>"
            + "GROUP BY d.id, d.name ORDER BY sales DESC LIMIT #{limit}"
            + "</script>")
    List<Map<String, Object>> dishAmountRank(@Param("payStatus") String payStatus,
                                             @Param("startDate") String startDate,
                                             @Param("endDate") String endDate,
                                             @Param("limit") int limit);

    /** 商家经营概览（单条聚合）：订单数、有效交易额(PAID)、退款金额(REFUNDED)、完成订单数；日期含结束日整天 */
    @Select("<script>"
            + "SELECT COUNT(*) AS orderCount, "
            + "COALESCE(SUM(CASE WHEN pay_status = '" + Constants.PAY_PAID + "' THEN pay_amount ELSE 0 END), 0) AS validAmount, "
            + "COALESCE(SUM(CASE WHEN pay_status = '" + Constants.PAY_REFUNDED + "' THEN pay_amount ELSE 0 END), 0) AS refundAmount, "
            + "IFNULL(SUM(order_status = '" + Constants.ORDER_COMPLETED + "'), 0) AS completedCount "
            + "FROM orders WHERE shop_id = #{shopId} "
            + "<if test=\"startDate != null and startDate != ''\">AND created_at &gt;= #{startDate} </if>"
            + "<if test=\"endDate != null and endDate != ''\">AND created_at &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY) </if>"
            + "</script>")
    Map<String, Object> merchantOverview(@Param("shopId") Long shopId,
                                         @Param("startDate") String startDate,
                                         @Param("endDate") String endDate);

    /** 商家热销菜品排行：本店已支付订单按销量件数降序 */
    @Select("<script>"
            + "SELECT d.name AS name, COALESCE(SUM(oi.quantity), 0) AS sales "
            + "FROM order_item oi JOIN orders o ON o.id = oi.order_id JOIN dish d ON d.id = oi.dish_id "
            + "WHERE o.shop_id = #{shopId} AND o.pay_status = #{payStatus} "
            + "<if test=\"startDate != null and startDate != ''\">AND o.created_at &gt;= #{startDate} </if>"
            + "<if test=\"endDate != null and endDate != ''\">AND o.created_at &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY) </if>"
            + "GROUP BY d.id, d.name ORDER BY sales DESC LIMIT #{limit}"
            + "</script>")
    List<Map<String, Object>> merchantDishRank(@Param("shopId") Long shopId,
                                               @Param("payStatus") String payStatus,
                                               @Param("startDate") String startDate,
                                               @Param("endDate") String endDate,
                                               @Param("limit") int limit);
}
