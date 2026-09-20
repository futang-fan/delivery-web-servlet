package com.deliveryweb.util;

/**
 * 系统常量：集中维护角色、账户状态等枚举取值与 Session 键名，
 * 业务代码禁止硬编码这些取值。
 * @author DeliveryWeb
 */
public final class Constants {

    /** 角色：顾客 */
    public static final String ROLE_CUSTOMER = "CUSTOMER";

    /** 角色：商家 */
    public static final String ROLE_SHOP = "SHOP";

    /** 角色：骑手 */
    public static final String ROLE_RIDER = "RIDER";

    /** 角色：平台管理员 */
    public static final String ROLE_ADMIN = "ADMIN";

    /** 账户状态：正常 */
    public static final String USER_STATUS_NORMAL = "NORMAL";

    /** 账户状态：禁用 */
    public static final String USER_STATUS_DISABLED = "DISABLED";

    /** 店铺审核状态：待审核 */
    public static final String SHOP_AUDIT_PENDING = "PENDING";

    /** 店铺审核状态：审核通过 */
    public static final String SHOP_AUDIT_APPROVED = "APPROVED";

    /** 店铺审核状态：审核拒绝 */
    public static final String SHOP_AUDIT_REJECTED = "REJECTED";

    /** 店铺营业状态：营业中 */
    public static final String BUSINESS_OPEN = "OPEN";

    /** 店铺营业状态：休息中 */
    public static final String BUSINESS_CLOSED = "CLOSED";

    /** 菜品上下架状态：上架 */
    public static final String DISH_STATUS_ON_SALE = "ON_SALE";

    /** 菜品上下架状态：下架 */
    public static final String DISH_STATUS_OFF_SALE = "OFF_SALE";

    /** 订单状态：待支付（设计书 表1.7） */
    public static final String ORDER_UNPAID = "UNPAID";

    /** 订单状态：待商家处理 */
    public static final String ORDER_PENDING_ACCEPT = "PENDING_ACCEPT";

    /** 订单状态：备餐中 */
    public static final String ORDER_PREPARING = "PREPARING";

    /** 订单状态：待配送 */
    public static final String ORDER_PENDING_DELIVERY = "PENDING_DELIVERY";

    /** 订单状态：配送中 */
    public static final String ORDER_DELIVERING = "DELIVERING";

    /** 订单状态：已送达 */
    public static final String ORDER_DELIVERED = "DELIVERED";

    /** 订单状态：已完成（终态） */
    public static final String ORDER_COMPLETED = "COMPLETED";

    /** 订单状态：已取消（终态） */
    public static final String ORDER_CANCELLED = "CANCELLED";

    /** 订单状态：已拒单（终态） */
    public static final String ORDER_REJECTED = "REJECTED";

    /** 支付状态：未支付 */
    public static final String PAY_UNPAID = "UNPAID";

    /** 支付状态：已支付 */
    public static final String PAY_PAID = "PAID";

    /** 支付状态：已退款 */
    public static final String PAY_REFUNDED = "REFUNDED";

    /** 配送状态：待抢单（设计书 表2.16） */
    public static final String DELIVERY_PENDING = "PENDING";

    /** 配送状态：已接单 */
    public static final String DELIVERY_ACCEPTED = "ACCEPTED";

    /** 取餐动作标识（/api/delivery/node 的 status 参数值；取餐后配送状态自动转 DELIVERING，PICKED 不作驻留状态） */
    public static final String DELIVERY_PICKED = "PICKED";

    /** 配送状态：配送中 */
    public static final String DELIVERY_DELIVERING = "DELIVERING";

    /** 配送状态：已送达 */
    public static final String DELIVERY_DELIVERED = "DELIVERED";

    /** 退款状态：待商家处理 */
    public static final String REFUND_PENDING = "PENDING";

    /** 退款状态：商家同意 */
    public static final String REFUND_SHOP_AGREED = "SHOP_AGREED";

    /** 退款状态：商家拒绝 */
    public static final String REFUND_SHOP_REJECTED = "SHOP_REJECTED";

    /** 退款状态：平台裁定同意（退款仲裁轻量版，商家超时不处理时管理员兜底） */
    public static final String REFUND_ADMIN_AGREED = "ADMIN_AGREED";

    /** 退款状态：平台裁定驳回（退款仲裁轻量版，不合理申请且商家未处理时由平台关闭，不退款） */
    public static final String REFUND_ADMIN_REJECTED = "ADMIN_REJECTED";

    /** 未支付订单超时阈值（分钟，设计书 2.2.4 超时取消） */
    public static final int ORDER_TIMEOUT_MINUTES = 15;

    /** 找回申请有效期（分钟）：超时管理员未处理自动作废 */
    public static final int RESET_REQUEST_TTL_MINUTES = 10;

    /** 找回验证码有效期（分钟）：管理员同意后须在此时间内使用 */
    public static final int RESET_CODE_TTL_MINUTES = 5;

    /** 找回申请冷却（毫秒）：同一用户两次申请的最小间隔 */
    public static final long RESET_REQUEST_COOLDOWN_MILLIS = 60 * 1000L;

    /** 找回验证码最大失败次数：达到即作废该申请 */
    public static final int RESET_CODE_MAX_FAILS = 5;

    /** 找回申请内存留存上限：超出时挤掉最旧一条（防内存膨胀） */
    public static final int RESET_STORE_MAX = 50;

    /** 手机号格式（表2.2）：11 位数字（UserService/ShopService/AddressService 共用） */
    public static final java.util.regex.Pattern PHONE_PATTERN = java.util.regex.Pattern.compile("^\\d{11}$");

    /** 超时扫描周期（秒，OrderTimeoutListener 定时器） */
    public static final int TIMEOUT_SCAN_SECONDS = 60;

    /** 已送达订单自动完成阈值（小时）：顾客超时未确认收货由平台自动完成（设计书表 1.7“超时自动完成”） */
    public static final int DELIVERED_AUTO_COMPLETE_HOURS = 24;

    /** 已送达自动完成扫描周期（秒）：阈值为小时级，周期长于未支付扫描 */
    public static final int DELIVERED_SCAN_SECONDS = 600;

    /** Session 中订单幂等令牌的键 */
    public static final String SESSION_ORDER_TOKEN = "ORDER_TOKEN";

    /** Session 中购物车的键（购物车存 Session 不落库） */
    public static final String SESSION_CART = "CART";

    /** Session 中保存当前登录用户的键名 */
    public static final String SESSION_LOGIN_USER = "LOGIN_USER";

    private Constants() {
    }
}
