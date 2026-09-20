package com.deliveryweb.pojo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 退款记录实体，对应数据表 refund。
 * 三态状态：待处理 / 商家同意 / 商家拒绝。
 */
public class Refund implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 退款记录编号（自增主键） */
    private Long id;

    /** 所属订单编号，逻辑外键 */
    private Long orderId;

    /** 退款原因 */
    private String reason;

    /** 退款状态：PENDING-待处理 / SHOP_AGREED-商家同意 / SHOP_REJECTED-商家拒绝 / ADMIN_AGREED-平台裁定同意 */
    private String status;

    /** 退款处理说明 */
    private String remark;

    /** 平台裁定说明（退款仲裁轻量版，refund.admin_remark） */
    private String adminRemark;

    /** 申请时间 */
    private LocalDateTime createdAt;

    public Refund() {
    }

    public Refund(Long id, Long orderId, String reason, String status,
                  String remark, LocalDateTime createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.reason = reason;
        this.status = status;
        this.remark = remark;
        this.createdAt = createdAt;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Refund{" +
                "id=" + id +
                ", orderId=" + orderId +
                ", reason='" + reason + '\'' +
                ", status='" + status + '\'' +
                ", remark='" + remark + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
