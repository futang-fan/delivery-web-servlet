package com.deliveryweb.service;

import com.deliveryweb.dao.*;
import com.deliveryweb.pojo.*;
import com.deliveryweb.util.*;

import javax.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 订单业务逻辑：
 * 创建订单（事务：校验 + 主表 + 明细 + 库存行级扣减 + 地址/价格快照）、模拟支付、取消未支付订单（库存恢复）。
 * 幂等令牌存 Session，下单时校验并销毁；金额一律以服务端当前菜品价格计算为准。
 *
 * @author DeliveryWeb
 */
public final class OrderService {

    /** 订单号前缀格式：年月日时分秒毫秒，后接 3 位随机数（uk_order_no 兜底） */
    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private OrderService() {
    }

    /** 签发幂等令牌：Session 保存，重复签发以最后一次为准 */
    public static String issueToken(HttpSession session) {
        String token = UUID.randomUUID().toString().replace("-", "");
        session.setAttribute(Constants.SESSION_ORDER_TOKEN, token);
        return token;
    }

    /**
     * 创建订单：事务内校验店铺营业/菜品上架/库存，写主表与明细并行级扣库存，保存地址与价格快照；
     * 初始状态 UNPAID。令牌缺失或不匹配直接拒绝（前端提交前重新获取令牌）。
     * 事务成功后清空购物车（设计书：下单成功后清空）。
     */
    public static Map<String, Object> createOrder(HttpSession session, Long customerId,
                                                   Long addressId, String remark, String token) {
        Object expected = session.getAttribute(Constants.SESSION_ORDER_TOKEN);
        session.removeAttribute(Constants.SESSION_ORDER_TOKEN);
        if (expected == null || token == null || !expected.equals(token)) {
            throw new BizException("订单令牌无效或已使用，请重新提交");
        }
        if (remark != null && remark.length() > 100) {
            throw new BizException("订单备注须为 100 字以内");
        }

        Map<String, Object> cartView = CartService.list(session);
        List<CartItem> cartItems = (List<CartItem>) cartView.get("items");
        if (cartItems.isEmpty()) {
            throw new BizException("购物车为空，请先加购");
        }
        Long shopId = (Long) cartView.get("shopId");

        Order created = MyBatisUtil.withTx(tx -> {
            ShopDao shopDao = tx.getMapper(ShopDao.class);
            DishDao dishDao = tx.getMapper(DishDao.class);
            AddressDao addressDao = tx.getMapper(AddressDao.class);
            OrderDao orderDao = tx.getMapper(OrderDao.class);
            OrderItemDao itemDao = tx.getMapper(OrderItemDao.class);

            Shop shop = shopDao.findById(shopId);
            if (shop == null || !Constants.BUSINESS_OPEN.equals(shop.getBusinessStatus())) {
                throw new BizException("店铺已休息，不能下单");
            }
            Address address = addressDao.findOwned(addressId, customerId);
            if (address == null) {
                throw new BizException("收货地址不存在");
            }

            LocalDateTime now = LocalDateTime.now();
            List<OrderItem> items = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            for (CartItem cartItem : cartItems) {
                Dish dish = dishDao.findById(cartItem.getDishId());
                if (dish == null || !Constants.DISH_STATUS_ON_SALE.equals(dish.getStatus())) {
                    throw new BizException("菜品「" + cartItem.getDishName() + "」已下架");
                }
                if (dishDao.deductStock(dish.getId(), cartItem.getQuantity(), Constants.DISH_STATUS_ON_SALE) != 1) {
                    throw new BizException("菜品「" + cartItem.getDishName() + "」库存不足");
                }
                BigDecimal subtotal = dish.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
                OrderItem item = new OrderItem();
                item.setDishId(dish.getId());
                item.setDishNameSnapshot(dish.getName());
                item.setPriceSnapshot(dish.getPrice());
                item.setQuantity(cartItem.getQuantity());
                item.setSubtotal(subtotal);
                items.add(item);
                total = total.add(subtotal);
            }

            Order order = new Order();
            order.setOrderNo(generateOrderNo());
            order.setCustomerId(customerId);
            order.setShopId(shopId);
            order.setAddressSnapshot(address.getReceiver() + "，" + address.getPhone() + "，" + address.getDetail());
            order.setTotalAmount(total);
            order.setPayAmount(total);
            order.setPayStatus(Constants.PAY_UNPAID);
            order.setOrderStatus(Constants.ORDER_UNPAID);
            order.setRemark(remark);
            order.setCreatedAt(now);
            order.setUpdatedAt(now);
            orderDao.insert(order);
            for (OrderItem item : items) {
                item.setOrderId(order.getId());
            }
            itemDao.batchInsert(items);
            return order;
        });

        CartService.clear(session);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", created.getId());
        result.put("orderNo", created.getOrderNo());
        return result;
    }

    /** 模拟支付：UNPAID -> PENDING_ACCEPT，payStatus -> PAID 并记录 paid_at；条件更新防重复支付 */
    public static void pay(Long customerId, Long orderId) {
        Order order = loadOwned(customerId, orderId);
        OrderStateMachine.assertTransfer(order.getOrderStatus(), Constants.ORDER_PENDING_ACCEPT);
        LocalDateTime now = LocalDateTime.now();
        int rows = MyBatisUtil.withTx(tx -> tx.getMapper(OrderDao.class).pay(
                orderId, Constants.ORDER_UNPAID, Constants.ORDER_PENDING_ACCEPT,
                Constants.PAY_PAID, now, now));
        if (rows != 1) {
            throw new BizException("订单状态已变化，支付失败");
        }
    }

    /** 取消未支付订单：-> CANCELLED，同一事务内按明细恢复库存 */
    public static void cancel(Long customerId, Long orderId) {
        Order order = loadOwned(customerId, orderId);
        OrderStateMachine.assertTransfer(order.getOrderStatus(), Constants.ORDER_CANCELLED);
        LocalDateTime now = LocalDateTime.now();
        MyBatisUtil.withTx(tx -> {
            OrderDao orderDao = tx.getMapper(OrderDao.class);
            OrderItemDao itemDao = tx.getMapper(OrderItemDao.class);
            DishDao dishDao = tx.getMapper(DishDao.class);
            int rows = orderDao.transferStatus(orderId, Constants.ORDER_UNPAID, Constants.ORDER_CANCELLED, now);
            if (rows != 1) {
                throw new BizException("订单状态已变化，取消失败");
            }
            for (OrderItem item : itemDao.listByOrder(orderId)) {
                dishDao.restoreStock(item.getDishId(), item.getQuantity());
            }
            return rows;
        });
    }

    /** 归属校验加载订单：仅本人订单可操作（越权防护） */
    private static Order loadOwned(Long customerId, Long orderId) {
        if (orderId == null) {
            throw new BizException("缺少订单参数");
        }
        Order order = MyBatisUtil.withMapper(OrderDao.class, dao -> dao.findById(orderId));
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (!order.getCustomerId().equals(customerId)) {
            throw new BizException("无权操作他人订单");
        }
        return order;
    }

    /** 四角色订单分页查询：顾客本人/商家本店/骑手承接/管理员全部，状态可选筛选；进入时惰性取消超时未支付单 */
    public static Page<Order> listByRole(String role, Long userId, String orderStatus, String payStatus,
                                         Page<Order> page) {
        cancelExpiredOrders();
        Long customerId;
        Long shopId;
        Long riderId;
        if (Constants.ROLE_CUSTOMER.equals(role)) {
            customerId = userId;
            shopId = null;
            riderId = null;
        } else if (Constants.ROLE_SHOP.equals(role)) {
            Shop shop = MyBatisUtil.withMapper(ShopDao.class, dao -> dao.findByOwnerId(userId));
            if (shop == null) {
                return page;
            }
            customerId = null;
            shopId = shop.getId();
            riderId = null;
        } else if (Constants.ROLE_RIDER.equals(role)) {
            customerId = null;
            shopId = null;
            riderId = userId;
        } else {
            customerId = null;
            shopId = null;
            riderId = null;
        }
        page.setTotal(MyBatisUtil.withMapper(OrderDao.class,
                dao -> dao.countByScope(customerId, shopId, riderId, orderStatus, payStatus)));
        if (page.getTotal() > 0) {
            page.setRecords(MyBatisUtil.withMapper(OrderDao.class,
                    dao -> dao.listByScope(customerId, shopId, riderId, orderStatus, payStatus,
                            page.offset(), page.getSize())));
        }
        return page;
    }

    /** 管理员订单监管分页：订单号/用户名/店名/日期区间/状态动态条件 */
    public static Page<Order> listForAdmin(String orderNo, String username, String shopName,
                                           String startDate, String endDate, String orderStatus,
                                           Page<Order> page) {
        cancelExpiredOrders();
        page.setTotal(MyBatisUtil.withMapper(OrderDao.class,
                dao -> dao.countForAdmin(orderNo, username, shopName, startDate, endDate, orderStatus)));
        if (page.getTotal() > 0) {
            page.setRecords(MyBatisUtil.withMapper(OrderDao.class,
                    dao -> dao.listForAdmin(orderNo, username, shopName, startDate, endDate, orderStatus,
                            page.offset(), page.getSize())));
        }
        return page;
    }

    /** 管理端订单导出行数上限：
     *  Servlet 截断判定与 DAO 取数共用同一值，防两处硬编码日后漂移；防误导出全表 */
    public static final int ADMIN_EXPORT_LIMIT = 5000;

    /** 管理端订单导出取数（/api/admin/orders/export）：与 listForAdmin 同条件但不分页；
     *  从而区分“恰好等于上限（数据完整）”与“超过上限（真截断）”；不走 Page.of 以免 size 被钳制到 50 */
    public static List<Order> listForAdminExport(String orderNo, String username, String shopName,
                                                 String startDate, String endDate, String orderStatus) {
        cancelExpiredOrders();
        return MyBatisUtil.withMapper(OrderDao.class,
                dao -> dao.listForAdmin(orderNo, username, shopName, startDate, endDate, orderStatus,
                        0, ADMIN_EXPORT_LIMIT + 1));
    }

    /** 取消扫描节流：30 秒内至多一次（列表接口与定时器共用；定时器周期 60s 不受影响） */
    private static final long CANCEL_SCAN_THROTTLE_MILLIS = 30 * 1000L;
    private static volatile long lastCancelScanMillis = 0L;

    /**
     * 超时取消未支付订单：OrderTimeoutListener 定时扫描与列表惰性检查共用。
     * 每笔在独立事务内条件流转 UNPAID→CANCELLED 并按明细恢复库存，返回取消笔数。
     */
    public static int cancelExpiredOrders() {
        long nowMillis = System.currentTimeMillis();
        if (nowMillis - lastCancelScanMillis < CANCEL_SCAN_THROTTLE_MILLIS) {
            return 0;
        }
        lastCancelScanMillis = nowMillis;
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(Constants.ORDER_TIMEOUT_MINUTES);
        List<Order> expired = MyBatisUtil.withMapper(OrderDao.class,
                dao -> dao.listExpiredUnpaid(Constants.PAY_UNPAID, Constants.ORDER_UNPAID, cutoff));
        int cancelled = 0;
        for (Order order : expired) {
            cancelled += MyBatisUtil.withTx(tx -> {
                OrderDao orderDao = tx.getMapper(OrderDao.class);
                OrderItemDao itemDao = tx.getMapper(OrderItemDao.class);
                DishDao dishDao = tx.getMapper(DishDao.class);
                int rows = orderDao.transferStatus(order.getId(), Constants.ORDER_UNPAID,
                        Constants.ORDER_CANCELLED, LocalDateTime.now());
                if (rows != 1) {
                    return 0;
                }
                for (OrderItem item : itemDao.listByOrder(order.getId())) {
                    dishDao.restoreStock(item.getDishId(), item.getQuantity());
                }
                return 1;
            });
        }
        return cancelled;
    }

    /**
     * 已送达订单超时自动完成：OrderTimeoutListener 第二任务调用。
     * 扫描 DELIVERED 且 delivered_at 早于截止时间的订单，每笔独立事务条件流转 DELIVERED→COMPLETED，返回完成笔数。
     */
    public static int completeExpiredDelivered() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(Constants.DELIVERED_AUTO_COMPLETE_HOURS);
        List<Order> expired = MyBatisUtil.withMapper(OrderDao.class,
                dao -> dao.listExpiredDelivered(Constants.ORDER_DELIVERED, cutoff));
        int completed = 0;
        for (Order order : expired) {
            completed += MyBatisUtil.withTx(tx -> {
                int rows = tx.getMapper(OrderDao.class).transferStatus(order.getId(),
                        Constants.ORDER_DELIVERED, Constants.ORDER_COMPLETED, LocalDateTime.now());
                return rows == 1 ? 1 : 0;
            });
        }
        return completed;
    }

    /** 商家接单：PENDING_ACCEPT -> PREPARING，同事务写 accepted_at */
    public static void accept(Long sellerId, Long orderId) {
        Order order = loadShopOwned(sellerId, orderId);
        OrderStateMachine.assertTransfer(order.getOrderStatus(), Constants.ORDER_PREPARING);
        LocalDateTime now = LocalDateTime.now();
        int rows = MyBatisUtil.withTx(tx -> tx.getMapper(OrderDao.class)
                .acceptWithTime(orderId, Constants.ORDER_PENDING_ACCEPT, Constants.ORDER_PREPARING, now, now));
        if (rows != 1) {
            throw new BizException("订单状态已变化，接单失败");
        }
    }

    /** 商家拒单：-> REJECTED，同事务生成 SHOP_AGREED 退款记录并恢复库存 */
    public static void reject(Long sellerId, Long orderId, String reason) {
        if (reason == null || reason.isEmpty()) {
            throw new BizException("拒单原因必填");
        }
        Order order = loadShopOwned(sellerId, orderId);
        OrderStateMachine.assertTransfer(order.getOrderStatus(), Constants.ORDER_REJECTED);
        LocalDateTime now = LocalDateTime.now();
        MyBatisUtil.withTx(tx -> {
            OrderDao orderDao = tx.getMapper(OrderDao.class);
            OrderItemDao itemDao = tx.getMapper(OrderItemDao.class);
            DishDao dishDao = tx.getMapper(DishDao.class);
            RefundDao refundDao = tx.getMapper(RefundDao.class);
            int rows = orderDao.transferStatus(orderId, Constants.ORDER_PENDING_ACCEPT, Constants.ORDER_REJECTED, now);
            if (rows != 1) {
                throw new BizException("订单状态已变化，拒单失败");
            }
            for (OrderItem item : itemDao.listByOrder(orderId)) {
                dishDao.restoreStock(item.getDishId(), item.getQuantity());
            }
            int refundRows = orderDao.refundPay(orderId, Constants.PAY_PAID,
                    Constants.PAY_REFUNDED, now);
            if (refundRows != 1 && !Constants.PAY_REFUNDED.equals(
                    orderDao.findById(orderId).getPayStatus())) {
                // 审查 P1（2026-09-12）：管理员仲裁已置 REFUNDED 的订单再拒单视为幂等成功
                throw new BizException("订单支付状态不允许退款");
            }
            Refund existing = refundDao.findByOrder(orderId);
            if (existing != null && (Constants.REFUND_PENDING.equals(existing.getStatus())
                    || Constants.REFUND_SHOP_REJECTED.equals(existing.getStatus())
                    || Constants.REFUND_ADMIN_REJECTED.equals(existing.getStatus()))) {
                int collapsed = refundDao.collapseOnReject(existing.getId(),
                        Constants.REFUND_SHOP_AGREED, "商家拒单自动退款（拒单原因：" + reason + "）",
                        Constants.REFUND_PENDING, Constants.REFUND_SHOP_REJECTED,
                        Constants.REFUND_ADMIN_REJECTED);
                if (collapsed != 1) {
                    throw new BizException("退款申请状态已变化，拒单失败");
                }
            } else if (existing == null) {
                Refund refund = new Refund();
                refund.setOrderId(orderId);
                refund.setReason(reason);
                refund.setStatus(Constants.REFUND_SHOP_AGREED);
                refund.setRemark("商家拒单自动退款");
                refund.setCreatedAt(now);
                refundDao.insert(refund);
            }
            return rows;
        });
    }

    /** 备餐完成：PREPARING -> PENDING_DELIVERY，同事务写 prepared_at并生成 PENDING 配送记录进入配送池 */
    public static void ready(Long sellerId, Long orderId) {
        Order order = loadShopOwned(sellerId, orderId);
        OrderStateMachine.assertTransfer(order.getOrderStatus(), Constants.ORDER_PENDING_DELIVERY);
        LocalDateTime now = LocalDateTime.now();
        MyBatisUtil.withTx(tx -> {
            OrderDao orderDao = tx.getMapper(OrderDao.class);
            DeliveryDao deliveryDao = tx.getMapper(DeliveryDao.class);
            int rows = orderDao.readyWithTime(orderId, Constants.ORDER_PREPARING, Constants.ORDER_PENDING_DELIVERY, now, now);
            if (rows != 1) {
                throw new BizException("订单状态已变化，备餐完成失败");
            }
            Delivery delivery = new Delivery();
            delivery.setOrderId(orderId);
            delivery.setStatus(Constants.DELIVERY_PENDING);
            delivery.setUpdatedAt(now);
            deliveryDao.insert(delivery);
            return rows;
        });
    }

    /** 商家订单归属校验：仅登录商家本人店铺的订单可操作（越权防护） */
    private static Order loadShopOwned(Long sellerId, Long orderId) {
        if (orderId == null) {
            throw new BizException("缺少订单参数");
        }
        Shop shop = MyBatisUtil.withMapper(ShopDao.class, dao -> dao.findByOwnerId(sellerId));
        if (shop == null) {
            throw new BizException("您还没有店铺，不能操作订单");
        }
        Order order = MyBatisUtil.withMapper(OrderDao.class, dao -> dao.findById(orderId));
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (!shop.getId().equals(order.getShopId())) {
            throw new BizException("无权操作他人店铺的订单");
        }
        return order;
    }

    /** 订单详情：按角色归属校验（本人/本店/承接骑手/管理员全部），聚合明细/配送/退款/评价与店名 */
    public static OrderDetailVO detail(String role, Long userId, Long orderId) {
        if (orderId == null) {
            throw new BizException("缺少订单参数");
        }
        Order order = MyBatisUtil.withMapper(OrderDao.class, dao -> dao.findById(orderId));
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (Constants.ROLE_CUSTOMER.equals(role)) {
            if (!order.getCustomerId().equals(userId)) {
                throw new BizException("无权查看他人订单");
            }
        } else if (Constants.ROLE_SHOP.equals(role)) {
            Shop shop = MyBatisUtil.withMapper(ShopDao.class, dao -> dao.findByOwnerId(userId));
            if (shop == null || !shop.getId().equals(order.getShopId())) {
                throw new BizException("无权查看他人店铺的订单");
            }
        } else if (Constants.ROLE_RIDER.equals(role)) {
            Delivery deliveryOfOrder = MyBatisUtil.withMapper(DeliveryDao.class, dao -> dao.findByOrder(orderId));
            if (deliveryOfOrder == null || !userId.equals(deliveryOfOrder.getRiderId())) {
                throw new BizException("无权查看他人配送的订单");
            }
        }
        OrderDetailVO vo = new OrderDetailVO();
        vo.setOrder(order);
        vo.setItems(MyBatisUtil.withMapper(OrderItemDao.class, dao -> dao.listByOrder(orderId)));
        vo.setDelivery(MyBatisUtil.withMapper(DeliveryDao.class, dao -> dao.findByOrder(orderId)));
        vo.setRefund(MyBatisUtil.withMapper(RefundDao.class, dao -> dao.findByOrder(orderId)));
        vo.setReview(MyBatisUtil.withMapper(ReviewDao.class, dao -> dao.findByOrder(orderId)));
        Shop shopOfOrder = MyBatisUtil.withMapper(ShopDao.class, dao -> dao.findById(order.getShopId()));
        if (shopOfOrder != null) {
            order.setShopName(shopOfOrder.getName());
        }
        return vo;
    }

    /** 顾客确认收货：DELIVERED→COMPLETED，归属校验在 findOwned */
    public static void receive(Long customerId, Long orderId) {
        if (orderId == null) {
            throw new BizException("缺少订单参数");
        }
        Order order = MyBatisUtil.withMapper(OrderDao.class, dao -> dao.findOwned(orderId, customerId));
        if (order == null) {
            throw new BizException("订单不存在或无权操作他人订单");
        }
        OrderStateMachine.assertTransfer(order.getOrderStatus(), Constants.ORDER_COMPLETED);
        int rows = MyBatisUtil.withTx(tx -> tx.getMapper(OrderDao.class)
                .transferStatus(orderId, order.getOrderStatus(), Constants.ORDER_COMPLETED, LocalDateTime.now()));
        if (rows == 0) {
            throw new BizException("订单状态已变化，请刷新重试");
        }
    }

    private static String generateOrderNo() {
        return LocalDateTime.now().format(ORDER_NO_FORMATTER)
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
    }
}
