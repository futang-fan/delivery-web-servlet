package com.deliveryweb.pojo;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 店铺评分摘要（聚合视图对象，非数据表实体）。
 * 由 review 表按 shop_id 聚合：评价总数、平均分、各星级数量。
 * 无评价时平均分为 0、各星级为 0（不返回空对象，避免前端 NaN）。
 */
public class ShopRatingSummary implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 评价总数 */
    private long reviewCount;

    /** 平均评分（1-5，保留一位小数） */
    private BigDecimal averageRating;

    /** 五星评价数 */
    private int star5;

    /** 四星评价数 */
    private int star4;

    /** 三星评价数 */
    private int star3;

    /** 二星评价数 */
    private int star2;

    /** 一星评价数 */
    private int star1;

    public ShopRatingSummary() {
    }

    public long getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(long reviewCount) {
        this.reviewCount = reviewCount;
    }

    public BigDecimal getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(BigDecimal averageRating) {
        this.averageRating = averageRating;
    }

    public int getStar5() {
        return star5;
    }

    public void setStar5(int star5) {
        this.star5 = star5;
    }

    public int getStar4() {
        return star4;
    }

    public void setStar4(int star4) {
        this.star4 = star4;
    }

    public int getStar3() {
        return star3;
    }

    public void setStar3(int star3) {
        this.star3 = star3;
    }

    public int getStar2() {
        return star2;
    }

    public void setStar2(int star2) {
        this.star2 = star2;
    }

    public int getStar1() {
        return star1;
    }

    public void setStar1(int star1) {
        this.star1 = star1;
    }
}
