package com.deliveryweb.util;

import java.util.*;

/**
 * 订单状态机：集中维护的状态转换表。
 * 所有订单状态变更前必须经 {@link #assertTransfer} 校验当前状态，
 * 非法转换抛出 BizException 且调用方保持原数据不变。
 */
public final class OrderStateMachine {

    /** 转换表：当前状态 -> 允许到达的状态集合；终态为空集 */
    private static final Map<String, Set<String>> TRANSITIONS = new HashMap<>();

    static {
        TRANSITIONS.put(Constants.ORDER_UNPAID,
                set(Constants.ORDER_PENDING_ACCEPT, Constants.ORDER_CANCELLED));
        TRANSITIONS.put(Constants.ORDER_PENDING_ACCEPT,
                set(Constants.ORDER_PREPARING, Constants.ORDER_REJECTED));
        TRANSITIONS.put(Constants.ORDER_PREPARING, set(Constants.ORDER_PENDING_DELIVERY));
        TRANSITIONS.put(Constants.ORDER_PENDING_DELIVERY, set(Constants.ORDER_DELIVERING));
        TRANSITIONS.put(Constants.ORDER_DELIVERING, set(Constants.ORDER_DELIVERED));
        TRANSITIONS.put(Constants.ORDER_DELIVERED, set(Constants.ORDER_COMPLETED));
        TRANSITIONS.put(Constants.ORDER_COMPLETED, Collections.emptySet());
        TRANSITIONS.put(Constants.ORDER_CANCELLED, Collections.emptySet());
        TRANSITIONS.put(Constants.ORDER_REJECTED, Collections.emptySet());
    }

    private OrderStateMachine() {
    }

    /** 判断 currentStatus -> targetStatus 是否为合法转换 */
    public static boolean canTransfer(String currentStatus, String targetStatus) {
        Set<String> targets = TRANSITIONS.get(currentStatus);
        return targets != null && targets.contains(targetStatus);
    }

    /** 校验转换合法性；非法时抛出业务异常（保持原数据不变由调用方事务保证） */
    public static void assertTransfer(String currentStatus, String targetStatus) {
        if (!canTransfer(currentStatus, targetStatus)) {
            throw new BizException("订单状态不允许该操作：" + currentStatus + " -> " + targetStatus);
        }
    }

    private static Set<String> set(String... statuses) {
        Set<String> result = new HashSet<>();
        Collections.addAll(result, statuses);
        return Collections.unmodifiableSet(result);
    }
}
