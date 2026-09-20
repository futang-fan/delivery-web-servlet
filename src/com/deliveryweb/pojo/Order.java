package com.deliveryweb.pojo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单主实体，对应数据表 orders。
 * 对外展示使用订单号 orderNo，内部关联使用自增主键 id。
 */
public class Order implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单编号，仅内部关联用（自增主键） */
    private Long id;

    /** 订单号，对外展示和查询 */
    private String orderNo;

    /** 顾客编号，逻辑外键 */
    private Long customerId;

    /** 店铺编号，逻辑外键 */
    private Long shopId;

    /** 收货地址快照，格式：收货人，电话，详细地址 */
    private String addressSnapshot;

    /** 商品总金额 */
    private BigDecimal totalAmount;

    /** 实付金额，无优惠活动时等于商品总金额 */
    private BigDecimal payAmount;

    /** 支付状态：UNPAID-未支付 / PAID-已支付 / REFUNDED-已退款 */
    private String payStatus;

    /** 支付时间 */
    private LocalDateTime paidAt;

    /** 商家接单时间（C1 时间线补全，orders.accepted_at） */
    private LocalDateTime acceptedAt;

    /** 备餐完成时间（C1 时间线补全，orders.prepared_at） */
    private LocalDateTime preparedAt;

    /** 订单状态，取值见详细设计书 表1.7 */
    private String orderStatus;

    /** 订单备注 */
    private String remark;

    /** 下单时间 */
    private LocalDateTime createdAt;

    /** 最后更新时间，状态变更时刷新 */
    private LocalDateTime updatedAt;

    /** 店铺名称（联表查询填充，非 orders 表列，P05/P09 列表展示用） */
    private String shopName;

    /** 顾客用户名（管理员订单查询联表填充，非 orders 表列） */
    private String customerName;

    /** 退款状态（列表联表 refund 填充，非 orders 表列；P05 退款可见性） */
    private String refundStatus;

    /** 菜品摘要（列表子查询 order_item 快照拼接，非 orders 表列；区分订单用） */
    private String dishSummary;

    public Order() {
    }

    public Order(Long id, String orderNo, Long customerId, Long shopId, String addressSnapshot,
                 BigDecimal totalAmount, BigDecimal payAmount, String payStatus, LocalDateTime paidAt,
                 String orderStatus, String remark, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.orderNo = orderNo;
        this.customerId = customerId;
        this.shopId = shopId;
        this.addressSnapshot = addressSnapshot;
        this.totalAmount = totalAmount;
        this.payAmount = payAmount;
        this.payStatus = payStatus;
        this.paidAt = paidAt;
        this.orderStatus = orderStatus;
        this.remark = remark;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Long getShopId() {
        return shopId;
    }

    public void setShopId(Long shopId) {
        this.shopId = shopId;
    }

    public String getAddressSnapshot() {
        return addressSnapshot;
    }

    public void setAddressSnapshot(String addressSnapshot) {
        this.addressSnapshot = addressSnapshot;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BigDecimal getPayAmount() {
        return payAmount;
    }

    public void setPayAmount(BigDecimal payAmount) {
        this.payAmount = payAmount;
    }

    public String getPayStatus() {
        return payStatus;
    }

    public void setPayStatus(String payStatus) {
        this.payStatus = payStatus;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(LocalDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public LocalDateTime getPreparedAt() {
        return preparedAt;
    }

    public void setPreparedAt(LocalDateTime preparedAt) {
        this.preparedAt = preparedAt;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getShopName() {
        return shopName;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getRefundStatus() {
        return refundStatus;
    }

    public void setRefundStatus(String refundStatus) {
        this.refundStatus = refundStatus;
    }

    public String getDishSummary() {
        return dishSummary;
    }

    public void setDishSummary(String dishSummary) {
        this.dishSummary = dishSummary;
    }

    @Override
    public String toString() {
        return "Order{" +
                "id=" + id +
                ", orderNo='" + orderNo + '\'' +
                ", customerId=" + customerId +
                ", shopId=" + shopId +
                ", addressSnapshot='" + addressSnapshot + '\'' +
                ", totalAmount=" + totalAmount +
                ", payAmount=" + payAmount +
                ", payStatus='" + payStatus + '\'' +
                ", paidAt=" + paidAt +
                ", orderStatus='" + orderStatus + '\'' +
                ", remark='" + remark + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
