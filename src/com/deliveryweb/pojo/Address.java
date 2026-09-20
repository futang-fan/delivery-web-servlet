package com.deliveryweb.pojo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 收货地址实体，对应数据表 address。
 */
public class Address implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 地址编号（自增主键） */
    private Long id;

    /** 所属用户编号，逻辑外键 */
    private Long userId;

    /** 收货人姓名 */
    private String receiver;

    /** 收货人电话 */
    private String phone;

    /** 详细收货地址 */
    private String detail;

    /** 是否默认地址：true 是、false 否 */
    private Boolean isDefault;

    /** 创建时间 */
    private LocalDateTime createdAt;

    public Address() {
    }

    public Address(Long id, Long userId, String receiver, String phone, String detail,
                   Boolean isDefault, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.receiver = receiver;
        this.phone = phone;
        this.detail = detail;
        this.isDefault = isDefault;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Address{" +
                "id=" + id +
                ", userId=" + userId +
                ", receiver='" + receiver + '\'' +
                ", phone='" + phone + '\'' +
                ", detail='" + detail + '\'' +
                ", isDefault=" + isDefault +
                ", createdAt=" + createdAt +
                '}';
    }
}
