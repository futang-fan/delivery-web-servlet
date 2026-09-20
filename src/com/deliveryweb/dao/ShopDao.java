package com.deliveryweb.dao;

import com.deliveryweb.pojo.Shop;
import com.deliveryweb.pojo.ShopRatingSummary;
import com.deliveryweb.util.Constants;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 店铺数据访问接口（MyBatis Mapper，注解实现）。
 */
public interface ShopDao {

    String BASE_COLUMNS = "id, owner_id, name, description, phone, address, contact, qualification, "
            + "audit_status, audit_reason, audited_at, auditor_id, business_status, created_at";

    @Select("SELECT " + BASE_COLUMNS + " FROM shop WHERE id = #{id}")
    Shop findById(@Param("id") Long id);

    @Select("SELECT " + BASE_COLUMNS + " FROM shop WHERE owner_id = #{ownerId}")
    Shop findByOwnerId(@Param("ownerId") Long ownerId);

    @Insert("INSERT INTO shop (owner_id, name, audit_status, business_status, created_at) "
            + "VALUES (#{ownerId}, #{name}, #{auditStatus}, #{businessStatus}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Shop shop);

    /** 入驻申请/整改重提：覆盖表2.3 五项资料，审核状态回到待审核并清空原拒绝原因 */
    @Update("UPDATE shop SET name = #{name}, contact = #{contact}, phone = #{phone}, "
            + "address = #{address}, qualification = #{qualification}, "
            + "audit_status = #{auditStatus}, audit_reason = NULL WHERE id = #{id}")
    int updateApplyInfo(Shop shop);

    /** 审核结论写入：状态、原因、时间与操作人一次更新 */
    @Update("UPDATE shop SET audit_status = #{auditStatus}, audit_reason = #{auditReason}, "
            + "audited_at = #{auditedAt}, auditor_id = #{auditorId} WHERE id = #{id}")
    int updateAudit(Shop shop);

    /** 店铺资料维护：名称/简介/电话/地址，带归属条件防止越权修改他人店铺 */
    @Update("UPDATE shop SET name = #{name}, description = #{description}, phone = #{phone}, "
            + "address = #{address} WHERE id = #{id} AND owner_id = #{ownerId}")
    int updateInfo(Shop shop);

    /** 营业状态切换：带归属条件 */
    @Update("UPDATE shop SET business_status = #{businessStatus} "
            + "WHERE id = #{id} AND owner_id = #{ownerId}")
    int updateBusinessStatus(Shop shop);

    /** 管理员强制休息：仅营业中店铺可被强制关闭，条件更新防并发 */
    @Update("UPDATE shop SET business_status = #{targetStatus} "
            + "WHERE id = #{id} AND business_status = #{currentStatus}")
    int updateBusinessStatusById(@Param("id") Long id,
                                 @Param("currentStatus") String currentStatus,
                                 @Param("targetStatus") String targetStatus);

    /** 顾客端店铺搜索：仅审核通过店铺，关键字匹配店名或菜品名，营业状态可选筛选；
     *  联表返回评分聚合与近 30 天已支付销量聚合，左连接保证无评价/无销量店铺不丢失；
     *  sort 支持 DEFAULT/RATING/SALES，"营业中排前"恒为第一排序键，s.id DESC 兜底 */
    @Select("<script>"
            + "SELECT " + BASE_COLUMNS + ", "
            + "IFNULL(r.avg_rating, 0) AS avg_rating, IFNULL(r.review_count, 0) AS review_count, "
            + "IFNULL(q.sales_count, 0) AS sales_count "
            + "FROM shop s "
            + "LEFT JOIN (SELECT shop_id, AVG(rating) AS avg_rating, COUNT(*) AS review_count "
            + "FROM review GROUP BY shop_id) r ON r.shop_id = s.id "
            + "LEFT JOIN (SELECT o.shop_id, SUM(oi.quantity) AS sales_count "
            + "FROM order_item oi JOIN orders o ON o.id = oi.order_id "
            + "WHERE o.pay_status = '" + Constants.PAY_PAID + "' AND o.created_at &gt;= DATE_SUB(NOW(), INTERVAL 30 DAY) "
            + "GROUP BY o.shop_id) q ON q.shop_id = s.id "
            + "WHERE s.audit_status = #{auditStatus} "
            + "<if test=\"keyword != null and keyword != ''\">"
            + "AND (s.name LIKE CONCAT('%', #{keyword}, '%') "
            + "OR EXISTS (SELECT 1 FROM dish d WHERE d.shop_id = s.id "
            + "AND d.name LIKE CONCAT('%', #{keyword}, '%'))) "
            + "</if>"
            + "<if test=\"businessStatus != null and businessStatus != ''\">"
            + "AND s.business_status = #{businessStatus} "
            + "</if>"
            + "ORDER BY (s.business_status = '" + Constants.BUSINESS_OPEN + "') DESC, "
            + "<choose>"
            + "<when test=\"sort == 'RATING'\">IFNULL(r.avg_rating, 0) DESC, </when>"
            + "<when test=\"sort == 'SALES'\">IFNULL(q.sales_count, 0) DESC, </when>"
            + "</choose>"
            + "s.id DESC LIMIT #{offset}, #{size}"
            + "</script>")
    List<Shop> searchForCustomer(@Param("auditStatus") String auditStatus,
                                 @Param("keyword") String keyword,
                                 @Param("businessStatus") String businessStatus,
                                 @Param("sort") String sort,
                                 @Param("offset") int offset,
                                 @Param("size") int size);

    @Select("<script>"
            + "SELECT COUNT(*) FROM shop s WHERE s.audit_status = #{auditStatus} "
            + "<if test=\"keyword != null and keyword != ''\">"
            + "AND (s.name LIKE CONCAT('%', #{keyword}, '%') "
            + "OR EXISTS (SELECT 1 FROM dish d WHERE d.shop_id = s.id "
            + "AND d.name LIKE CONCAT('%', #{keyword}, '%'))) "
            + "</if>"
            + "<if test=\"businessStatus != null and businessStatus != ''\">"
            + "AND s.business_status = #{businessStatus} "
            + "</if>"
            + "</script>")
    long countForCustomer(@Param("auditStatus") String auditStatus,
                          @Param("keyword") String keyword,
                          @Param("businessStatus") String businessStatus);

    /** 管理员店铺列表：按审核状态/营业状态可选筛选，待审核排前便于处理 */
    @Select("<script>"
            + "SELECT " + BASE_COLUMNS + " FROM shop s "
            + "<where>"
            + "<if test=\"auditStatus != null and auditStatus != ''\">s.audit_status = #{auditStatus} </if>"
            + "<if test=\"businessStatus != null and businessStatus != ''\">AND s.business_status = #{businessStatus} </if>"
            + "</where>"
            + "ORDER BY (s.audit_status = '" + Constants.SHOP_AUDIT_PENDING + "') DESC, s.id DESC LIMIT #{offset}, #{size}"
            + "</script>")
    List<Shop> searchForAdmin(@Param("auditStatus") String auditStatus,
                              @Param("businessStatus") String businessStatus,
                              @Param("offset") int offset,
                              @Param("size") int size);

    @Select("<script>"
            + "SELECT COUNT(*) FROM shop s "
            + "<where>"
            + "<if test=\"auditStatus != null and auditStatus != ''\">s.audit_status = #{auditStatus} </if>"
            + "<if test=\"businessStatus != null and businessStatus != ''\">AND s.business_status = #{businessStatus} </if>"
            + "</where>"
            + "</script>")
    long countForAdmin(@Param("auditStatus") String auditStatus,
                       @Param("businessStatus") String businessStatus);

    /** 店铺评分摘要：评价数、平均分、各星级分布；无评价时计数 0、平均 0（聚合恒返回一行） */
    @Select("SELECT COUNT(*) AS review_count, IFNULL(AVG(rating), 0) AS average_rating, "
            + "IFNULL(SUM(rating = 5), 0) AS star5, IFNULL(SUM(rating = 4), 0) AS star4, "
            + "IFNULL(SUM(rating = 3), 0) AS star3, IFNULL(SUM(rating = 2), 0) AS star2, "
            + "IFNULL(SUM(rating = 1), 0) AS star1 "
            + "FROM review WHERE shop_id = #{shopId}")
    ShopRatingSummary ratingSummary(@Param("shopId") Long shopId);
}
