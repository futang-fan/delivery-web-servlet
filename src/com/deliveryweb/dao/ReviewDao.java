package com.deliveryweb.dao;

import com.deliveryweb.pojo.Review;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 评价数据访问接口（MyBatis Mapper，注解实现）。
 * 订单号唯一约束保证一单一次。
 */
public interface ReviewDao {

    String BASE_COLUMNS = "id, order_id, customer_id, shop_id, rating, content, created_at";

    @Insert("INSERT INTO review (order_id, customer_id, shop_id, rating, content, created_at) "
            + "VALUES (#{orderId}, #{customerId}, #{shopId}, #{rating}, #{content}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Review review);

    /** 按订单查评价（唯一约束至多一条，重复评价业务查重用） */
    @Select("SELECT " + BASE_COLUMNS + " FROM review WHERE order_id = #{orderId}")
    Review findByOrder(@Param("orderId") Long orderId);

    /** 店铺评价分页：新评价排前（P03 店铺详情页展示） */
    @Select("SELECT " + BASE_COLUMNS + " FROM review WHERE shop_id = #{shopId} "
            + "ORDER BY id DESC LIMIT #{offset}, #{size}")
    List<Review> listByShop(@Param("shopId") Long shopId, @Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM review WHERE shop_id = #{shopId}")
    long countByShop(@Param("shopId") Long shopId);
}
