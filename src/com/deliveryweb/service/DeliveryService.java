package com.deliveryweb.service;

import com.deliveryweb.dao.DeliveryDao;
import com.deliveryweb.dao.OrderDao;
import com.deliveryweb.dao.ShopDao;
import com.deliveryweb.pojo.Delivery;
import com.deliveryweb.pojo.DeliveryVO;
import com.deliveryweb.pojo.Order;
import com.deliveryweb.pojo.Shop;
import com.deliveryweb.util.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 配送业务逻辑（骑手抢单、履约节点、配送说明、配送记录查询）。
 * 抢单在事务内二次校验状态 + 条件更新，并发仅一人成功；
 * 履约节点为 取餐、送达 两个动作：
 * 取餐（status=PICKED）配送状态自动 已接单→配送中 并记 picked_at，订单同步 PENDING_DELIVERY→DELIVERING；
 * 送达（status=DELIVERED）配送状态 配送中→已送达 并记 delivered_at，订单同步 DELIVERING→DELIVERED；禁跳步。
 */
public final class DeliveryService {

    /** 配送说明长度上限（与菜品简介同口径） */
    private static final int REMARK_MAX = 200;

    private DeliveryService() {
    }

    /** 配送池分页：仅待抢单记录 */
    public static Page<DeliveryVO> pool(Page<DeliveryVO> page) {
        page.setTotal(MyBatisUtil.withMapper(DeliveryDao.class,
                dao -> dao.countPool(Constants.DELIVERY_PENDING)));
        if (page.getTotal() > 0) {
            page.setRecords(MyBatisUtil.withMapper(DeliveryDao.class,
                    dao -> dao.poolList(Constants.DELIVERY_PENDING, page.offset(), page.getSize())));
        }
        return page;
    }

    /** 抢单：事务内二次校验 + 条件更新，影响行数 0 即已被其他骑手抢占 */
    public static Long grab(Long riderId, Long deliveryId) {
        if (deliveryId == null) {
            throw new BizException("缺少配送单参数");
        }
        return MyBatisUtil.withTx(tx -> {
            DeliveryDao dao = tx.getMapper(DeliveryDao.class);
            Delivery delivery = dao.findById(deliveryId);
            if (delivery == null) {
                throw new BizException("配送记录不存在");
            }
            if (!Constants.DELIVERY_PENDING.equals(delivery.getStatus())) {
                throw new BizException("手慢了，订单已被抢走");
            }
            LocalDateTime now = LocalDateTime.now();
            int rows = dao.grab(deliveryId, riderId, Constants.DELIVERY_PENDING,
                    Constants.DELIVERY_ACCEPTED, now, now);
            if (rows == 0) {
                throw new BizException("手慢了，订单已被抢走");
            }
            return deliveryId;
        });
    }

    /**
     * 履约节点流转：targetStatus 仅接受 PICKED（取餐动作）/DELIVERED（送达动作）。
     * 取餐后配送状态自动转 DELIVERING（真实平台流转，无独立“开始配送”按钮）。
     * 配送状态与订单状态联动在同一个事务内完成，任一条件更新失败整体回滚。
     */
    public static void node(Long riderId, Long deliveryId, String targetStatus) {
        if (deliveryId == null) {
            throw new BizException("缺少配送单参数");
        }
        String currentDelivery;
        String newDelivery;
        String targetOrder;
        if (Constants.DELIVERY_PICKED.equals(targetStatus)) {
            currentDelivery = Constants.DELIVERY_ACCEPTED;
            newDelivery = Constants.DELIVERY_DELIVERING;
            targetOrder = Constants.ORDER_DELIVERING;
        } else if (Constants.DELIVERY_DELIVERED.equals(targetStatus)) {
            currentDelivery = Constants.DELIVERY_DELIVERING;
            newDelivery = Constants.DELIVERY_DELIVERED;
            targetOrder = Constants.ORDER_DELIVERED;
        } else {
            throw new BizException("无效的履约节点：" + targetStatus);
        }
        MyBatisUtil.withTx(tx -> {
            DeliveryDao dao = tx.getMapper(DeliveryDao.class);
            Delivery delivery = dao.findById(deliveryId);
            if (delivery == null) {
                throw new BizException("配送记录不存在");
            }
            if (!riderId.equals(delivery.getRiderId())) {
                throw new BizException("未接单的骑手不能更新该订单状态");
            }
            if (!currentDelivery.equals(delivery.getStatus())) {
                throw new BizException("履约节点须按 已接单→取餐(自动配送中)→送达 顺序流转，禁止跳步");
            }
            LocalDateTime now = LocalDateTime.now();
            if (targetOrder != null) {
                OrderDao orderDao = tx.getMapper(OrderDao.class);
                Order order = orderDao.findById(delivery.getOrderId());
                if (order == null) {
                    throw new BizException("订单不存在");
                }
                OrderStateMachine.assertTransfer(order.getOrderStatus(), targetOrder);
                int orderRows = orderDao.transferStatus(order.getId(), order.getOrderStatus(), targetOrder, now);
                if (orderRows == 0) {
                    throw new BizException("订单状态已变化，请刷新重试");
                }
            }
            int rows = dao.transferNode(deliveryId, currentDelivery, newDelivery,
                    Constants.DELIVERY_PICKED.equals(targetStatus) ? now : null,
                    Constants.DELIVERY_DELIVERED.equals(targetStatus) ? now : null,
                    now);
            if (rows == 0) {
                throw new BizException("配送状态已变化，请刷新重试");
            }
            return null;
        });
    }

    /** 配送说明/异常反馈：仅本人承接的配送可写 */
    public static void remark(Long riderId, Long deliveryId, String remark) {
        if (deliveryId == null) {
            throw new BizException("缺少配送单参数");
        }
        if (remark == null || remark.trim().isEmpty()) {
            throw new BizException("请填写配送说明");
        }
        if (remark.trim().length() > REMARK_MAX) {
            throw new BizException("配送说明不能超过 " + REMARK_MAX + " 字");
        }
        int rows = MyBatisUtil.withTx(tx -> tx.getMapper(DeliveryDao.class)
                .updateRemark(deliveryId, riderId, remark.trim(), LocalDateTime.now()));
        if (rows == 0) {
            throw new BizException("只能反馈本人承接配送的说明");
        }
    }

    /** 本人履约记录分页：状态可选筛选 */
    public static Page<DeliveryVO> my(Long riderId, String status, Page<DeliveryVO> page) {
        page.setTotal(MyBatisUtil.withMapper(DeliveryDao.class,
                dao -> dao.countMy(riderId, status)));
        if (page.getTotal() > 0) {
            page.setRecords(MyBatisUtil.withMapper(DeliveryDao.class,
                    dao -> dao.myList(riderId, status, page.offset(), page.getSize())));
        }
        return page;
    }

    /** 配送详情：顾客本人/本店商家/承接骑手/管理员可见，其余拒绝 */
    public static DeliveryVO detail(String role, Long userId, Long orderId) {
        if (orderId == null) {
            throw new BizException("缺少订单参数");
        }
        Order order = MyBatisUtil.withMapper(OrderDao.class, dao -> dao.findById(orderId));
        if (order == null) {
            throw new BizException("订单不存在");
        }
        DeliveryVO vo = MyBatisUtil.withMapper(DeliveryDao.class, dao -> dao.findVoByOrder(orderId));
        if (vo == null) {
            throw new BizException("该订单还没有配送记录");
        }
        boolean allowed = Constants.ROLE_ADMIN.equals(role)
                || order.getCustomerId().equals(userId)
                || riderOwned(vo, userId);
        if (!allowed && Constants.ROLE_SHOP.equals(role)) {
            Shop shop = MyBatisUtil.withMapper(ShopDao.class, dao -> dao.findByOwnerId(userId));
            allowed = shop != null && shop.getId().equals(order.getShopId());
        }
        if (!allowed) {
            throw new BizException("无权查看该订单的配送信息");
        }
        return vo;
    }

    /** 骑手战绩卡数据：今日送达 / 在途（已接单+配送中）/ 累计送达 */
    public static Map<String, Object> riderStats(Long riderId) {
        return MyBatisUtil.withMapper(DeliveryDao.class, dao -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("todayDelivered", dao.countTodayDelivered(riderId));
            data.put("activeCount", dao.countActive(riderId));
            data.put("deliveredTotal", dao.countDeliveredTotal(riderId));
            return data;
        });
    }

    /** 骑手归属：配送记录已承接且承接人为本人 */
    private static boolean riderOwned(DeliveryVO vo, Long userId) {
        return vo.getRiderId() != null && vo.getRiderId().equals(userId);
    }
}
