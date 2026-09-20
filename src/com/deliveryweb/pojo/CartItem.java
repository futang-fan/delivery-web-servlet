package com.deliveryweb.pojo;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 购物车项（值对象，不落库）：记录加入购物车时的菜品名称与价格快照。
 * 购物车整体存于 Session，同一购物车只允许同一家店铺的商品。
 */
public class CartItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 菜品编号 */
    private Long dishId;

    /** 所属店铺编号，用于同店约束 */
    private Long shopId;

    /** 菜品名称（加入时快照） */
    private String dishName;

    /** 单价（加入时快照，提交订单前会重新校验） */
    private BigDecimal price;

    /** 数量 */
    private Integer quantity;

    /** 小计 = 单价 × 数量 */
    private BigDecimal subtotal;

    public CartItem() {
    }

    public CartItem(Long dishId, Long shopId, String dishName, BigDecimal price,
                    Integer quantity, BigDecimal subtotal) {
        this.dishId = dishId;
        this.shopId = shopId;
        this.dishName = dishName;
        this.price = price;
        this.quantity = quantity;
        this.subtotal = subtotal;
    }

    public Long getDishId() {
        return dishId;
    }

    public void setDishId(Long dishId) {
        this.dishId = dishId;
    }

    public Long getShopId() {
        return shopId;
    }

    public void setShopId(Long shopId) {
        this.shopId = shopId;
    }

    public String getDishName() {
        return dishName;
    }

    public void setDishName(String dishName) {
        this.dishName = dishName;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    @Override
    public String toString() {
        return "CartItem{" +
                "dishId=" + dishId +
                ", shopId=" + shopId +
                ", dishName='" + dishName + '\'' +
                ", price=" + price +
                ", quantity=" + quantity +
                ", subtotal=" + subtotal +
                '}';
    }
}
