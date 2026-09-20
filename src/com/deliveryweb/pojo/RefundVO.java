package com.deliveryweb.pojo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款信息聚合（值对象）：退款记录 + 订单关键列一次返回。
 * 商家待处理列表展示订单号与实付金额，避免前端逐行二次请求。
 */
public class RefundVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 退款记录编号 */
    private Long refundId;

    /** 所属订单编号 */
    private Long orderId;

    /** 订单号（展示用） */
    private String orderNo;

    /** 订单实付金额（全额退款金额） */
    private BigDecimal payAmount;

    /** 顾客申请原因 */
    private String reason;

    /** 退款状态：PENDING/SHOP_AGREED/SHOP_REJECTED */
    private String status;

    /** 商家处理说明 */
    private String remark;

    /** 平台裁定说明（退款仲裁轻量版） */
    private String adminRemark;

    /** 店铺名称（管理员仲裁列表联表展示） */
    private String shopName;

    /** 顾客用户名（管理员仲裁列表联表展示） */
    private String customerName;

    /** 申请时间 */
    private LocalDateTime createdAt;

    public RefundVO() {
    }

    public Long getRefundId() {
        return refundId;
    }

    public void setRefundId(Long refundId) {
        this.refundId = refundId;
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

    public BigDecimal getPayAmount() {
        return payAmount;
    }

    public void setPayAmount(BigDecimal payAmount) {
        this.payAmount = payAmount;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getAdminRemark() {
        return adminRemark;
    }

    public void setAdminRemark(String adminRemark) {
        this.adminRemark = adminRemark;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
