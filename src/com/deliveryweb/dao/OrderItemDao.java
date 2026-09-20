package com.deliveryweb.dao;

import com.deliveryweb.pojo.OrderItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 订单明细数据访问接口（MyBatis Mapper，注解实现）。
 * 明细保存下单时名称与价格快照。
 */
public interface OrderItemDao {

    String BASE_COLUMNS = "id, order_id, dish_id, dish_name_snapshot, price_snapshot, quantity, subtotal";

    /** 明细批量插入（下单事务内一次写入） */
    @Insert("<script>"
            + "INSERT INTO order_item (order_id, dish_id, dish_name_snapshot, price_snapshot, quantity, subtotal) VALUES "
            + "<foreach collection=\"items\" item=\"it\" separator=\",\">"
            + "(#{it.orderId}, #{it.dishId}, #{it.dishNameSnapshot}, #{it.priceSnapshot}, #{it.quantity}, #{it.subtotal})"
            + "</foreach>"
            + "</script>")
    int batchInsert(@Param("items") List<OrderItem> items);

    @Select("SELECT " + BASE_COLUMNS + " FROM order_item WHERE order_id = #{orderId} ORDER BY id")
    List<OrderItem> listByOrder(@Param("orderId") Long orderId);
}
