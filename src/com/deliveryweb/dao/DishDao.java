package com.deliveryweb.dao;

import com.deliveryweb.pojo.Dish;
import com.deliveryweb.util.Constants;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 菜品数据访问接口（MyBatis Mapper，注解实现）。
 * 同店名称唯一由 uk_shop_name 约束 + 应用层 findByShopAndName 双重保障。
 */
public interface DishDao {

    String BASE_COLUMNS = "id, shop_id, name, description, price, stock, image_url, status, created_at";

    @Select("SELECT " + BASE_COLUMNS + " FROM dish WHERE id = #{id}")
    Dish findById(@Param("id") Long id);

    @Select("SELECT " + BASE_COLUMNS + " FROM dish WHERE shop_id = #{shopId} AND name = #{name}")
    Dish findByShopAndName(@Param("shopId") Long shopId, @Param("name") String name);

    /** 店铺菜品列表；status 为空返回全部（商家看全部），非空则按状态筛选（顾客只看上架）；
     *  左连接返回近 30 天已支付销量件数 sales_count */
    @Select("<script>"
            + "SELECT " + BASE_COLUMNS + ", IFNULL(q.sales_count, 0) AS sales_count "
            + "FROM dish "
            + "LEFT JOIN (SELECT oi.dish_id, SUM(oi.quantity) AS sales_count "
            + "FROM order_item oi JOIN orders o ON o.id = oi.order_id "
            + "WHERE o.pay_status = '" + Constants.PAY_PAID + "' AND o.created_at &gt;= DATE_SUB(NOW(), INTERVAL 30 DAY) "
            + "GROUP BY oi.dish_id) q ON q.dish_id = dish.id "
            + "WHERE dish.shop_id = #{shopId} "
            + "<if test=\"status != null and status != ''\">AND dish.status = #{status} </if>"
            + "ORDER BY dish.id DESC"
            + "</script>")
    List<Dish> listByShop(@Param("shopId") Long shopId, @Param("status") String status);

    @Insert("INSERT INTO dish (shop_id, name, description, price, stock, image_url, status, created_at) "
            + "VALUES (#{shopId}, #{name}, #{description}, #{price}, #{stock}, #{imageUrl}, #{status}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Dish dish);

    @Update("UPDATE dish SET name = #{name}, description = #{description}, price = #{price}, "
            + "stock = #{stock}, image_url = #{imageUrl} WHERE id = #{id} AND shop_id = #{shopId}")
    int update(Dish dish);

    @Update("UPDATE dish SET status = #{status} WHERE id = #{id} AND shop_id = #{shopId}")
    int updateStatus(@Param("id") Long id, @Param("shopId") Long shopId, @Param("status") String status);

    /** 库存行级条件扣减（防超卖，设计书 2.2.2）：下架或库存不足时影响行数为 0 */
    @Update("UPDATE dish SET stock = stock - #{quantity} WHERE id = #{id} AND status = #{status} AND stock >= #{quantity}")
    int deductStock(@Param("id") Long id, @Param("quantity") Integer quantity, @Param("status") String status);

    /** 库存恢复（取消订单/拒单退款） */
    @Update("UPDATE dish SET stock = stock + #{quantity} WHERE id = #{id}")
    int restoreStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    /** 顾客全局菜品搜索（跨店、仅在售、审核通过店铺）：联表返回店铺名与营业状态，
     *  并左连接近 30 天已支付销量件数 sales_count，分页 */
    @Select("<script>"
            + "SELECT d.id, d.shop_id, d.name, d.description, d.price, d.stock, d.image_url, d.status, d.created_at,"
            + " s.name AS shop_name, s.business_status, IFNULL(q.sales_count, 0) AS sales_count "
            + "FROM dish d JOIN shop s ON s.id = d.shop_id "
            + "LEFT JOIN (SELECT oi.dish_id, SUM(oi.quantity) AS sales_count "
            + "FROM order_item oi JOIN orders o ON o.id = oi.order_id "
            + "WHERE o.pay_status = '" + Constants.PAY_PAID + "' AND o.created_at &gt;= DATE_SUB(NOW(), INTERVAL 30 DAY) "
            + "GROUP BY oi.dish_id) q ON q.dish_id = d.id "
            + "WHERE d.status = '" + Constants.DISH_STATUS_ON_SALE + "' AND s.audit_status = '" + Constants.SHOP_AUDIT_APPROVED + "' "
            + "<if test=\"keyword != null and keyword != ''\">"
            + "AND (d.name LIKE CONCAT('%', #{keyword}, '%') OR s.name LIKE CONCAT('%', #{keyword}, '%')) </if>"
            + "<if test=\"shopId != null\">AND d.shop_id = #{shopId} </if>"
            + "<if test=\"minPrice != null\">AND d.price &gt;= #{minPrice} </if>"
            + "<if test=\"maxPrice != null\">AND d.price &lt;= #{maxPrice} </if>"
            + "ORDER BY d.id DESC LIMIT #{offset}, #{size}"
            + "</script>")
    List<Dish> searchDish(@Param("keyword") String keyword,
                          @Param("shopId") Long shopId,
                          @Param("minPrice") BigDecimal minPrice,
                          @Param("maxPrice") BigDecimal maxPrice,
                          @Param("offset") int offset,
                          @Param("size") int size);

    /** 全局菜品搜索计数（条件与 searchDish 一致） */
    @Select("<script>"
            + "SELECT COUNT(*) FROM dish d JOIN shop s ON s.id = d.shop_id "
            + "WHERE d.status = '" + Constants.DISH_STATUS_ON_SALE + "' AND s.audit_status = '" + Constants.SHOP_AUDIT_APPROVED + "' "
            + "<if test=\"keyword != null and keyword != ''\">"
            + "AND (d.name LIKE CONCAT('%', #{keyword}, '%') OR s.name LIKE CONCAT('%', #{keyword}, '%')) </if>"
            + "<if test=\"shopId != null\">AND d.shop_id = #{shopId} </if>"
            + "<if test=\"minPrice != null\">AND d.price &gt;= #{minPrice} </if>"
            + "<if test=\"maxPrice != null\">AND d.price &lt;= #{maxPrice} </if>"
            + "</script>")
    long countSearch(@Param("keyword") String keyword,
                     @Param("shopId") Long shopId,
                     @Param("minPrice") BigDecimal minPrice,
                     @Param("maxPrice") BigDecimal maxPrice);
}
