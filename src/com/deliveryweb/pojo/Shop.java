package com.deliveryweb.pojo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 店铺实体，对应数据表 shop。
 * 一商家对应一店铺，包含入驻审核状态与营业状态；
 * contact 与 qualification 为表 2.3 入驻资料在编码阶段的补充列。
 */
public class Shop implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 店铺编号（自增主键） */
    private Long id;

    /** 商家用户编号，逻辑外键 */
    private Long ownerId;

    /** 店铺名称 */
    private String name;

    /** 店铺简介 */
    private String description;

    /** 联系电话 */
    private String phone;

    /** 经营地址，兼作取餐地址 */
    private String address;

    /** 联系人姓名，来自入驻申请（表2.3） */
    private String contact;

    /** 资质说明，供管理员审核参考（表2.3） */
    private String qualification;

    /** 审核状态：PENDING-待审核 / APPROVED-通过 / REJECTED-拒绝 */
    private String auditStatus;

    /** 审核原因，拒绝时必填 */
    private String auditReason;

    /** 审核时间 */
    private LocalDateTime auditedAt;

    /** 审核人编号，逻辑外键 */
    private Long auditorId;

    /** 营业状态：OPEN-营业 / CLOSED-休息 */
    private String businessStatus;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 评分摘要（聚合瞬态字段，/api/shop/detail 返回，非数据表列） */
    private ShopRatingSummary ratingSummary;

    /** 平均评分（列表聚合瞬态字段，/api/shop/list 联表返回，无评价为 0） */
    private Double avgRating;

    /** 评价数（列表聚合瞬态字段，/api/shop/list 联表返回） */
    private Long reviewCount;

    /** 近 30 天已支付销量件数（列表聚合瞬态字段，/api/shop/list 联表返回） */
    private Long salesCount;

    public Shop() {
    }

    public Shop(Long id, Long ownerId, String name, String description, String phone,
                String address, String contact, String qualification, String auditStatus,
                String auditReason, LocalDateTime auditedAt, Long auditorId,
                String businessStatus, LocalDateTime createdAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.name = name;
        this.description = description;
        this.phone = phone;
        this.address = address;
        this.contact = contact;
        this.qualification = qualification;
        this.auditStatus = auditStatus;
        this.auditReason = auditReason;
        this.auditedAt = auditedAt;
        this.auditorId = auditorId;
        this.businessStatus = businessStatus;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public String getQualification() {
        return qualification;
    }

    public void setQualification(String qualification) {
        this.qualification = qualification;
    }

    public String getAuditStatus() {
        return auditStatus;
    }

    public void setAuditStatus(String auditStatus) {
        this.auditStatus = auditStatus;
    }

    public String getAuditReason() {
        return auditReason;
    }

    public void setAuditReason(String auditReason) {
        this.auditReason = auditReason;
    }

    public LocalDateTime getAuditedAt() {
        return auditedAt;
    }

    public void setAuditedAt(LocalDateTime auditedAt) {
        this.auditedAt = auditedAt;
    }

    public Long getAuditorId() {
        return auditorId;
    }

    public void setAuditorId(Long auditorId) {
        this.auditorId = auditorId;
    }

    public String getBusinessStatus() {
        return businessStatus;
    }

    public void setBusinessStatus(String businessStatus) {
        this.businessStatus = businessStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public ShopRatingSummary getRatingSummary() {
        return ratingSummary;
    }

    public void setRatingSummary(ShopRatingSummary ratingSummary) {
        this.ratingSummary = ratingSummary;
    }

    public Double getAvgRating() {
        return avgRating;
    }

    public void setAvgRating(Double avgRating) {
        this.avgRating = avgRating;
    }

    public Long getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(Long reviewCount) {
        this.reviewCount = reviewCount;
    }

    public Long getSalesCount() {
        return salesCount;
    }

    public void setSalesCount(Long salesCount) {
        this.salesCount = salesCount;
    }

    @Override
    public String toString() {
        return "Shop{" +
                "id=" + id +
                ", ownerId=" + ownerId +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", phone='" + phone + '\'' +
                ", address='" + address + '\'' +
                ", contact='" + contact + '\'' +
                ", qualification='" + qualification + '\'' +
                ", auditStatus='" + auditStatus + '\'' +
                ", auditReason='" + auditReason + '\'' +
                ", auditedAt=" + auditedAt +
                ", auditorId=" + auditorId +
                ", businessStatus='" + businessStatus + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
