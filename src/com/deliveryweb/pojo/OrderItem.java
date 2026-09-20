package com.deliveryweb.pojo;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单明细实体，对应数据表 order_item。
 * 保存下单时菜品名称与价格快照，历史账单不随菜品修改而变化。
 */
public class OrderItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 明细编号（自增主键） */
    private Long id;

    /** 所属订单编号，逻辑外键 */
    private Long orderId;

    /** 菜品编号，逻辑外键 */
    private Long dishId;

    /** 下单时菜品名称快照 */
    private String dishNameSnapshot;

    /** 下单时价格快照 */
    private BigDecimal priceSnapshot;

    /** 购买数量，大于零 */
    private Integer quantity;

    /** 小计金额，价格快照乘以数量 */
    private BigDecimal subtotal;

    public OrderItem() {
    }

    public OrderItem(Long id, Long orderId, Long dishId, String dishNameSnapshot,
                     BigDecimal priceSnapshot, Integer quantity, BigDecimal subtotal) {
        this.id = id;
        this.orderId = orderId;
        this.dishId = dishId;
        this.dishNameSnapshot = dishNameSnapshot;
        this.priceSnapshot = priceSnapshot;
        this.quantity = quantity;
        this.subtotal = subtotal;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getDishId() {
        return dishId;
    }

    public void setDishId(Long dishId) {
        this.dishId = dishId;
    }

    public String getDishNameSnapshot() {
        return dishNameSnapshot;
    }

    public void setDishNameSnapshot(String dishNameSnapshot) {
        this.dishNameSnapshot = dishNameSnapshot;
    }

    public BigDecimal getPriceSnapshot() {
        return priceSnapshot;
    }

    public void setPriceSnapshot(BigDecimal priceSnapshot) {
        this.priceSnapshot = priceSnapshot;
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
        return "OrderItem{" +
                "id=" + id +
                ", orderId=" + orderId +
                ", dishId=" + dishId +
                ", dishNameSnapshot='" + dishNameSnapshot + '\'' +
                ", priceSnapshot=" + priceSnapshot +
                ", quantity=" + quantity +
                ", subtotal=" + subtotal +
                '}';
    }
}
