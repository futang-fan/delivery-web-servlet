package com.deliveryweb.pojo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 配送记录实体，对应数据表 delivery。
 * 一个订单对应一条配送记录，五段配送状态按序流转。
 */
public class Delivery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 配送记录编号（自增主键） */
    private Long id;

    /** 所属订单编号，唯一约束，一单一记录 */
    private Long orderId;

    /** 骑手编号，抢单前为空，逻辑外键 */
    private Long riderId;

    /** 配送状态：PENDING / ACCEPTED / PICKED / DELIVERING / DELIVERED */
    private String status;

    /** 抢单时间 */
    private LocalDateTime acceptedAt;

    /** 取餐时间 */
    private LocalDateTime pickedAt;

    /** 送达时间 */
    private LocalDateTime deliveredAt;

    /** 配送说明 */
    private String remark;

    /** 最后更新时间，节点变更时刷新 */
    private LocalDateTime updatedAt;

    public Delivery() {
    }

    public Delivery(Long id, Long orderId, Long riderId, String status, LocalDateTime acceptedAt,
                    LocalDateTime pickedAt, LocalDateTime deliveredAt, String remark,
                    LocalDateTime updatedAt) {
        this.id = id;
        this.orderId = orderId;
        this.riderId = riderId;
        this.status = status;
        this.acceptedAt = acceptedAt;
        this.pickedAt = pickedAt;
        this.deliveredAt = deliveredAt;
        this.remark = remark;
        this.updatedAt = updatedAt;
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

    @Override
    public String toString() {
        return "Delivery{" +
                "id=" + id +
                ", orderId=" + orderId +
                ", riderId=" + riderId +
                ", status='" + status + '\'' +
                ", acceptedAt=" + acceptedAt +
                ", pickedAt=" + pickedAt +
                ", deliveredAt=" + deliveredAt +
                ", remark='" + remark + '\'' +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
