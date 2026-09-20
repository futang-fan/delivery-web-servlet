package com.deliveryweb.pojo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评价实体，对应数据表 review。
 * 订单号唯一约束保证同一订单只能评价一次。
 */
public class Review implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 评价编号（自增主键） */
    private Long id;

    /** 所属订单编号，唯一约束保证一单一次 */
    private Long orderId;

    /** 顾客编号，逻辑外键 */
    private Long customerId;

    /** 店铺编号，便于店铺维度统计评分 */
    private Long shopId;

    /** 评分，1 至 5 星 */
    private Integer rating;

    /** 评价文字 */
    private String content;

    /** 评价时间 */
    private LocalDateTime createdAt;

    public Review() {
    }

    public Review(Long id, Long orderId, Long customerId, Long shopId, Integer rating,
                  String content, LocalDateTime createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.customerId = customerId;
        this.shopId = shopId;
        this.rating = rating;
        this.content = content;
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

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Review{" +
                "id=" + id +
                ", orderId=" + orderId +
                ", customerId=" + customerId +
                ", shopId=" + shopId +
                ", rating=" + rating +
                ", content='" + content + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
