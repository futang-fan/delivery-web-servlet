package com.deliveryweb.pojo;

import java.io.Serializable;
import java.util.List;

/**
 * 订单详情聚合（值对象）：订单主表 + 明细 + 配送 + 退款 + 评价一次返回。
 * 状态时间线由前端按各 *_at 时间戳拼装；。
 */
public class OrderDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单主表信息 */
    private Order order;

    /** 订单明细（含菜品名称与价格快照） */
    private List<OrderItem> items;

    /** 配送记录（备餐完成后存在） */
    private Delivery delivery;

    /** 退款记录（拒单自动退款或顾客申请后存在） */
    private Refund refund;

    /** 评价（D8 交付，此前为 null） */
    private Review review;

    public OrderDetailVO() {
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    public Delivery getDelivery() {
        return delivery;
    }

    public void setDelivery(Delivery delivery) {
        this.delivery = delivery;
    }

    public Refund getRefund() {
        return refund;
    }

    public void setRefund(Refund refund) {
        this.refund = refund;
    }

    public Review getReview() {
        return review;
    }

    public void setReview(Review review) {
        this.review = review;
    }

    @Override
    public String toString() {
        return "OrderDetailVO{" +
                "order=" + order +
                ", items=" + items +
                ", delivery=" + delivery +
                ", refund=" + refund +
                ", review=" + review +
                '}';
    }
}
