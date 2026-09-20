package com.deliveryweb.service;

import com.deliveryweb.dao.OrderDao;
import com.deliveryweb.dao.ReviewDao;
import com.deliveryweb.pojo.Order;
import com.deliveryweb.pojo.Review;
import com.deliveryweb.util.BizException;
import com.deliveryweb.util.Constants;
import com.deliveryweb.util.MyBatisUtil;
import com.deliveryweb.util.Page;

import java.time.LocalDateTime;

/**
 * 评价业务逻辑（订单评价）。
 * 仅 COMPLETED 订单的本人顾客可评价；一单一次（业务查重 + 订单号唯一约束双保险）；
 * 评分 1-5 星，评价文字选填且不超 500 字。
 */
public final class ReviewService {

    /** 评价文字长度上限（表2.14 content VARCHAR(500)） */
    private static final int CONTENT_MAX = 500;

    private ReviewService() {
    }

    /** 提交评价：事务内校验归属/状态/查重后写入，shopId 取自订单便于店铺维度统计 */
    public static void submit(Long customerId, Long orderId, Integer rating, String content) {
        if (orderId == null) {
            throw new BizException("缺少订单参数");
        }
        if (rating == null || rating < 1 || rating > 5) {
            throw new BizException("评分须为 1-5 星");
        }
        String text = content == null ? null : content.trim();
        if (text != null && text.length() > CONTENT_MAX) {
            throw new BizException("评价内容不能超过 " + CONTENT_MAX + " 字");
        }
        MyBatisUtil.withTx(tx -> {
            OrderDao orderDao = tx.getMapper(OrderDao.class);
            Order order = orderDao.findById(orderId);
            if (order == null) {
                throw new BizException("订单不存在");
            }
            if (!order.getCustomerId().equals(customerId)) {
                throw new BizException("无权评价他人订单");
            }
            if (!Constants.ORDER_COMPLETED.equals(order.getOrderStatus())) {
                throw new BizException("仅已完成订单可评价");
            }
            ReviewDao reviewDao = tx.getMapper(ReviewDao.class);
            if (reviewDao.findByOrder(orderId) != null) {
                throw new BizException("该订单已评价过，不能重复评价");
            }
            Review review = new Review();
            review.setOrderId(orderId);
            review.setCustomerId(customerId);
            review.setShopId(order.getShopId());
            review.setRating(rating);
            review.setContent(text);
            review.setCreatedAt(LocalDateTime.now());
            reviewDao.insert(review);
            return null;
        });
    }

    /** 店铺评价分页 */
    public static Page<Review> byShop(Long shopId, Page<Review> page) {
        if (shopId == null) {
            throw new BizException("缺少店铺参数");
        }
        page.setTotal(MyBatisUtil.withMapper(ReviewDao.class, dao -> dao.countByShop(shopId)));
        if (page.getTotal() > 0) {
            page.setRecords(MyBatisUtil.withMapper(ReviewDao.class,
                    dao -> dao.listByShop(shopId, page.offset(), page.getSize())));
        }
        return page;
    }

    /** 评价存在性：控制前端评价入口显隐 */
    public static boolean exists(Long customerId, Long orderId) {
        if (orderId == null) {
            throw new BizException("缺少订单参数");
        }
        // P2-2 修复（2026-09-13）：补归属校验，仅本人订单可查评价状态（IDOR 收口）
        Order order = MyBatisUtil.withMapper(OrderDao.class, dao -> dao.findById(orderId));
        if (order == null || !order.getCustomerId().equals(customerId)) {
            throw new BizException("无权查询他人订单的评价状态");
        }
        return MyBatisUtil.withMapper(ReviewDao.class, dao -> dao.findByOrder(orderId)) != null;
    }
}
