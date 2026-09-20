package com.deliveryweb.pojo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 配送信息聚合（值对象）：配送记录 + 订单/店铺关键列一次返回。
 * 配送池（骑手未承接）与本人履约记录共用本结构，pool 场景 riderId 为空。
 */
public class DeliveryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 配送记录编号 */
    private Long deliveryId;

    /** 所属订单编号 */
    private Long orderId;

    /** 订单号（展示用） */
    private String orderNo;

    /** 订单状态（表1.7） */
    private String orderStatus;

    /** 订单总额 */
    private BigDecimal totalAmount;

    /** 实付金额 */
    private BigDecimal payAmount;

    /** 店铺名称（取餐店铺） */
    private String shopName;

    /** 取餐地址（店铺经营地址） */
    private String shopAddress;

    /** 收货地址快照 */
    private String addressSnapshot;

    /** 承接骑手编号，待抢单时为空 */
    private Long riderId;

    /** 配送状态：PENDING/ACCEPTED/PICKED/DELIVERING/DELIVERED */
    private String status;

    /** 抢单时间 */
    private LocalDateTime acceptedAt;

    /** 取餐时间 */
    private LocalDateTime pickedAt;

    /** 送达时间 */
    private LocalDateTime deliveredAt;

    /** 配送说明/异常反馈 */
    private String remark;

    /** 最后更新时间 */
    private LocalDateTime updatedAt;

    public DeliveryVO() {
    }

    public Long getDeliveryId() {
        return deliveryId;
    }

    public void setDeliveryId(Long deliveryId) {
        this.deliveryId = deliveryId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
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

    public String getShopName() {
        return shopName;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
    }

    public String getShopAddress() {
        return shopAddress;
    }

    public void setShopAddress(String shopAddress) {
        this.shopAddress = shopAddress;
    }

    public String getAddressSnapshot() {
        return addressSnapshot;
    }

    public void setAddressSnapshot(String addressSnapshot) {
        this.addressSnapshot = addressSnapshot;
    }

    public Long getRiderId() {
        return riderId;
    }

    public void setRiderId(Long riderId) {
        this.riderId = riderId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(LocalDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public LocalDateTime getPickedAt() {
        return pickedAt;
    }

    public void setPickedAt(LocalDateTime pickedAt) {
        this.pickedAt = pickedAt;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(LocalDateTime deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
