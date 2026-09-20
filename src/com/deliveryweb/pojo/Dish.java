package com.deliveryweb.pojo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 菜品实体，对应数据表 dish。
 * 同一店铺内菜品名称唯一，支持软删除式上下架。
 */
public class Dish implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 菜品编号（自增主键） */
    private Long id;

    /** 所属店铺编号，逻辑外键 */
    private Long shopId;

    /** 菜品名称 */
    private String name;

    /** 菜品描述 */
    private String description;

    /** 售价，非负，单位为元 */
    private BigDecimal price;

    /** 库存数量，非负 */
    private Integer stock;

    /** 菜品图片地址 */
    private String imageUrl;

    /** 上下架状态：ON_SALE-上架 / OFF_SALE-下架 */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 店铺名称（联表查询瞬态字段，全局搜索展示用） */
    private String shopName;

    /** 店铺营业状态（联表查询瞬态字段，全局搜索展示用） */
    private String businessStatus;

    /** 近 30 天已支付销量件数（联表聚合瞬态字段，热销标数据源，非数据表列） */
    private Long salesCount;

    public Dish() {
    }

    public Dish(Long id, Long shopId, String name, String description, BigDecimal price,
                Integer stock, String imageUrl, String status, LocalDateTime createdAt) {
        this.id = id;
        this.shopId = shopId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.imageUrl = imageUrl;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getShopId() {
        return shopId;
    }

    public void setShopId(Long shopId) {
        this.shopId = shopId;
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

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getShopName() {
        return shopName;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
    }

    public String getBusinessStatus() {
        return businessStatus;
    }

    public void setBusinessStatus(String businessStatus) {
        this.businessStatus = businessStatus;
    }

    public Long getSalesCount() {
        return salesCount;
    }

    public void setSalesCount(Long salesCount) {
        this.salesCount = salesCount;
    }

    @Override
    public String toString() {
        return "Dish{" +
                "id=" + id +
                ", shopId=" + shopId +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", price=" + price +
                ", stock=" + stock +
                ", imageUrl='" + imageUrl + '\'' +
                ", status='" + status + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
