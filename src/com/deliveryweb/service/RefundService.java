package com.deliveryweb.service;

import com.deliveryweb.dao.OrderDao;
import com.deliveryweb.dao.RefundDao;
import com.deliveryweb.dao.ShopDao;
import com.deliveryweb.pojo.Order;
import com.deliveryweb.pojo.Refund;
import com.deliveryweb.pojo.RefundVO;
import com.deliveryweb.pojo.Shop;
import com.deliveryweb.util.BizException;
import com.deliveryweb.util.Constants;
import com.deliveryweb.util.MyBatisUtil;
import com.deliveryweb.util.Page;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 退款业务逻辑（退款申请、商家处理退款）。
 * 申请：已支付且未完成（PENDING_ACCEPT~DELIVERED）、无既有退款记录、按实付全额；
 * 处理：同意→订单支付状态置已退款（防重复计入交易统计），拒绝→拒绝终态；
 * 处理走条件更新（WHERE status='PENDING'），并发/重复处理仅一人成功。
 */
public final class RefundService {

    /** 允许申请退款的订单状态：已支付且未完成 */
    private static final Set<String> APPLICABLE_ORDER_STATUS = new HashSet<>(Arrays.asList(
            Constants.ORDER_PENDING_ACCEPT, Constants.ORDER_PREPARING, Constants.ORDER_PENDING_DELIVERY,
            Constants.ORDER_DELIVERING, Constants.ORDER_DELIVERED));

    /** 退款原因长度上限 */
    private static final int REASON_MAX = 200;

    /** 处理说明/裁定说明长度上限（口径对齐 init.sql 的 refund.remark/admin_remark VARCHAR(200)） */
    private static final int REMARK_MAX = 200;

    private RefundService() {
    }

    /** 顾客退款申请：校验归属/支付/状态/查重后生成待处理全额退款记录 */
    public static void apply(Long customerId, Long orderId, String reason) {
        if (orderId == null) {
            throw new BizException("缺少订单参数");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new BizException("请填写退款原因");
        }
        if (reason.trim().length() > REASON_MAX) {
            throw new BizException("退款原因不能超过 " + REASON_MAX + " 字");
        }
        MyBatisUtil.withTx(tx -> {
            OrderDao orderDao = tx.getMapper(OrderDao.class);
            Order order = orderDao.findById(orderId);
            if (order == null) {
                throw new BizException("订单不存在");
            }
            if (!order.getCustomerId().equals(customerId)) {
                throw new BizException("无权为他人订单申请退款");
            }
            if (!Constants.PAY_PAID.equals(order.getPayStatus())) {
                throw new BizException("仅已支付订单可申请退款");
            }
            if (!APPLICABLE_ORDER_STATUS.contains(order.getOrderStatus())) {
                throw new BizException("当前订单状态不能申请退款");
            }
            RefundDao refundDao = tx.getMapper(RefundDao.class);
            if (refundDao.findByOrder(orderId) != null) {
                throw new BizException("该订单已存在退款记录，不能重复申请");
            }
            Refund refund = new Refund();
            refund.setOrderId(orderId);
            refund.setReason(reason.trim());
            refund.setStatus(Constants.REFUND_PENDING);
            refund.setCreatedAt(LocalDateTime.now());
            refundDao.insert(refund);
            return null;
        });
    }

    /** 商家处理退款：同意→SHOP_AGREED 且订单支付状态置已退款；拒绝→SHOP_REJECTED 终态 */
    public static void handle(Long sellerId, Long refundId, boolean agree, String remark) {
        if (refundId == null) {
            throw new BizException("缺少退款单参数");
        }
        if (remark != null && remark.trim().length() > REMARK_MAX) {
            throw new BizException("处理说明不能超过 " + REMARK_MAX + " 字");
        }
        MyBatisUtil.withTx(tx -> {
            RefundDao refundDao = tx.getMapper(RefundDao.class);
            Refund refund = refundDao.findById(refundId);
            if (refund == null) {
                throw new BizException("退款记录不存在");
            }
            OrderDao orderDao = tx.getMapper(OrderDao.class);
            Order order = orderDao.findById(refund.getOrderId());
            if (order == null) {
                throw new BizException("订单不存在");
            }
            Shop shop = tx.getMapper(ShopDao.class).findByOwnerId(sellerId);
            if (shop == null || !shop.getId().equals(order.getShopId())) {
                throw new BizException("无权处理其他店铺的退款");
            }
            String target = agree ? Constants.REFUND_SHOP_AGREED : Constants.REFUND_SHOP_REJECTED;
            int rows = refundDao.handle(refundId, Constants.REFUND_PENDING, target, remark);
            if (rows == 0) {
                throw new BizException("该退款已被处理，请勿重复操作");
            }
            if (agree) {
                int orderRows = orderDao.refundPay(order.getId(), Constants.PAY_PAID,
                        Constants.PAY_REFUNDED, LocalDateTime.now());
                if (orderRows == 0 && !Constants.PAY_REFUNDED.equals(
                        orderDao.findById(order.getId()).getPayStatus())) {
                    // 审查 P0-1（2026-09-12）：拒单已置 REFUNDED 时视为幂等成功，不再回滚卡死待处理记录
                    throw new BizException("订单支付状态不允许退款");
                }
            }
            return null;
        });
    }

    /** 本店待处理退款分页；无店铺时返回空页 */
    public static Page<RefundVO> pending(Long sellerId, Page<RefundVO> page) {
        Shop shop = MyBatisUtil.withMapper(ShopDao.class, dao -> dao.findByOwnerId(sellerId));
        if (shop == null) {
            return page;
        }
        page.setTotal(MyBatisUtil.withMapper(RefundDao.class,
                dao -> dao.countPending(shop.getId(), Constants.REFUND_PENDING)));
        if (page.getTotal() > 0) {
            page.setRecords(MyBatisUtil.withMapper(RefundDao.class,
                    dao -> dao.pendingList(shop.getId(), Constants.REFUND_PENDING, page.offset(), page.getSize())));
        }
        return page;
    }

    /** 全平台待处理退款分页（管理员仲裁列表） */
    public static Page<RefundVO> pendingForAdmin(Page<RefundVO> page) {
        page.setTotal(MyBatisUtil.withMapper(RefundDao.class,
                dao -> dao.countPendingForAdmin(Constants.REFUND_PENDING)));
        if (page.getTotal() > 0) {
            page.setRecords(MyBatisUtil.withMapper(RefundDao.class,
                    dao -> dao.pendingListForAdmin(Constants.REFUND_PENDING, page.offset(), page.getSize())));
        }
        return page;
    }

    /** 平台裁定（退款仲裁轻量版）：商家超时不处理时管理员双向兜底。
     *  同意→ADMIN_AGREED 且同事务订单 payStatus→REFUNDED；驳回→ADMIN_REJECTED 终态、理由必填、不退款；
     *  均为条件更新（WHERE status='PENDING'），重复裁定影响行数 0 即被拒 */
    public static void adminHandle(Long refundId, boolean agree, String adminRemark) {
        if (refundId == null) {
            throw new BizException("缺少退款单参数");
        }
        if (!agree && (adminRemark == null || adminRemark.trim().isEmpty())) {
            throw new BizException("驳回退款必须填写裁定理由");
        }
        if (adminRemark != null && adminRemark.trim().length() > REMARK_MAX) {
            throw new BizException("裁定说明不能超过 " + REMARK_MAX + " 字");
        }
        MyBatisUtil.withTx(tx -> {
            RefundDao refundDao = tx.getMapper(RefundDao.class);
            Refund refund = refundDao.findById(refundId);
            if (refund == null) {
                throw new BizException("退款记录不存在");
            }
            if (!Constants.REFUND_PENDING.equals(refund.getStatus())) {
                throw new BizException("该退款已被处理，请勿重复操作");
            }
            OrderDao orderDao = tx.getMapper(OrderDao.class);
            Order order = orderDao.findById(refund.getOrderId());
            if (order == null) {
                throw new BizException("订单不存在");
            }
            if (agree) {
                // 审查 P0-1（2026-09-12）：拒单自动退款已置 REFUNDED 的订单，裁定同意视为幂等成功（不再重复退款）
                boolean alreadyRefunded = Constants.PAY_REFUNDED.equals(order.getPayStatus());
                if (!alreadyRefunded && !Constants.PAY_PAID.equals(order.getPayStatus())) {
                    throw new BizException("订单支付状态不允许退款");
                }
                int rows = refundDao.adminDecide(refundId, Constants.REFUND_PENDING,
                        Constants.REFUND_ADMIN_AGREED, adminRemark);
                if (rows == 0) {
                    throw new BizException("该退款已被处理，请勿重复操作");
                }
                if (!alreadyRefunded) {
                    int orderRows = orderDao.refundPay(order.getId(), Constants.PAY_PAID,
                            Constants.PAY_REFUNDED, LocalDateTime.now());
                    if (orderRows == 0) {
                        throw new BizException("订单支付状态不允许退款");
                    }
                }
                return null;
            }
            int rows = refundDao.adminDecide(refundId, Constants.REFUND_PENDING,
                    Constants.REFUND_ADMIN_REJECTED, adminRemark.trim());
            if (rows == 0) {
                throw new BizException("该退款已被处理，请勿重复操作");
            }
            return null;
        });
    }
}
