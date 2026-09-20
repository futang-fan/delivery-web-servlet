package com.deliveryweb.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 找回密码申请与一次性验证码的内存存储。
 * 无数据库表：申请与验证码均存内存、重启即失，与登录失败限流（UserService.LOGIN_FAILS）同口径，
 * 课设单实例规模可接受。约束（取值见 Constants）：
 * 申请 {RESET_REQUEST_TTL_MINUTES} 分钟过期；验证码 {RESET_CODE_TTL_MINUTES} 分钟有效、
 * 错 {RESET_CODE_MAX_FAILS} 次作废；同一用户 {RESET_REQUEST_COOLDOWN_MILLIS} 冷却；
 * 同一用户同时至多一份申请（新建即作废旧申请）；留存上限 {RESET_STORE_MAX} 条。
 */
public final class ResetCodeStore {

    /** 申请状态：待管理员处理 */
    public static final String STATUS_PENDING = "PENDING";

    /** 申请状态：已同意并下发验证码（验证码仅管理端可见，线下告知申请人） */
    public static final String STATUS_CODE_ISSUED = "CODE_ISSUED";

    /** 申请状态：管理员作废 */
    public static final String STATUS_REJECTED = "REJECTED";

    /** 申请状态：超时过期 */
    public static final String STATUS_EXPIRED = "EXPIRED";

    /** 找回申请单条记录 */
    public static final class Request {
        private final long id;
        private final String username;
        private final String phoneMasked;
        private final long createdAtMillis;
        private final java.time.LocalDateTime createdAt;
        private final AtomicInteger failCount = new AtomicInteger();
        private volatile String status = STATUS_PENDING;
        private volatile String code;
        private volatile long codeExpiry;

        private Request(long id, String username, String phoneMasked, long createdAtMillis) {
            this.id = id;
            this.username = username;
            this.phoneMasked = phoneMasked;
            this.createdAtMillis = createdAtMillis;
            this.createdAt = java.time.LocalDateTime.now();
        }

        public long getId() {
            return id;
        }

        public String getUsername() {
            return username;
        }

        public String getPhoneMasked() {
            return phoneMasked;
        }

        /** 申请时间（JsonUtil 统一序列化为 yyyy-MM-dd HH:mm:ss） */
        public java.time.LocalDateTime getCreatedAt() {
            return createdAt;
        }

        /** 申请时间毫秒值（内部过期判定与排序用） */
        public long getCreatedAtMillis() {
            return createdAtMillis;
        }

        public String getStatus() {
            return status;
        }

        /** 已下发且未过期的验证码（仅管理端列表展示用；过期返回 null） */
        public String getVisibleCode() {
            return (code != null && codeExpiry > System.currentTimeMillis()) ? code : null;
        }
    }

    private static final AtomicLong SEQ = new AtomicLong();
    private static final Map<Long, Request> REQUESTS = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_REQUEST_AT = new ConcurrentHashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();

    private ResetCodeStore() {
    }

    /**
     * 创建找回申请：同一用户 60 秒冷却，新建即作废该用户旧申请（保证同时至多一份有效申请）。
     * 调用前须由 Service 层完成用户名与手机号匹配校验。
     */
    public static Request create(String username, String phoneMasked) {
        long now = System.currentTimeMillis();
        Long last = LAST_REQUEST_AT.get(username);
        if (last != null && now - last < Constants.RESET_REQUEST_COOLDOWN_MILLIS) {
            throw new BizException("申请过于频繁，请稍后再试");
        }
        for (Request old : REQUESTS.values()) {
            if (old.username.equals(username)) {
                REQUESTS.remove(old.id);
            }
        }
        prune(now);
        if (REQUESTS.size() >= Constants.RESET_STORE_MAX) {
            REQUESTS.values().stream()
                    .min(Comparator.comparingLong(Request::getCreatedAtMillis))
                    .ifPresent(oldest -> REQUESTS.remove(oldest.id));
        }
        Request request = new Request(SEQ.incrementAndGet(), username, phoneMasked, now);
        REQUESTS.put(request.id, request);
        LAST_REQUEST_AT.put(username, now);
        return request;
    }

    /** 全量申请列表（新→旧），供管理端"找回审批"页签展示 */
    public static List<Request> list() {
        prune(System.currentTimeMillis());
        List<Request> all = new ArrayList<>(REQUESTS.values());
        all.sort(Comparator.comparingLong(Request::getId).reversed());
        return all;
    }

    /**
     * 管理员裁定：同意生成 6 位一次性验证码（5 分钟有效）并返回给管理端展示；
     * 拒绝则作废申请（返回 null）。仅待处理状态的申请可裁定。
     */
    public static String decide(long requestId, boolean agree) {
        Request request = REQUESTS.get(requestId);
        long now = System.currentTimeMillis();
        if (request == null || !STATUS_PENDING.equals(request.status)) {
            throw new BizException("申请不存在或已处理");
        }
        if (now - request.createdAtMillis > Constants.RESET_REQUEST_TTL_MINUTES * 60 * 1000L) {
            request.status = STATUS_EXPIRED;
            throw new BizException("申请已过期，请让用户重新提交");
        }
        if (!agree) {
            request.status = STATUS_REJECTED;
            return null;
        }
        request.code = String.format("%06d", RANDOM.nextInt(1_000_000));
        request.codeExpiry = now + Constants.RESET_CODE_TTL_MINUTES * 60 * 1000L;
        request.status = STATUS_CODE_ISSUED;
        return request.code;
    }

    /**
     * 校验并消费验证码：绑定用户名、一次性使用；连续错 {RESET_CODE_MAX_FAILS} 次作废申请。
     * 验证码比较使用常量时间方法；命中即先从表中移除再校验（审查 P2-1：原子化，
     * 杜绝并发下同一验证码被消费两次）。
     */
    public static void verifyAndConsume(String username, String code) {
        long now = System.currentTimeMillis();
        Request consumed = null;
        for (Request request : REQUESTS.values()) {
            if (!request.username.equals(username) || request.code == null) {
                continue;
            }
            if (request.codeExpiry <= now) {
                request.status = STATUS_EXPIRED;
                continue;
            }
            // 先移除占坑：同一申请并发校验时仅一个线程能成功移除
            if (REQUESTS.remove(request.id) == null) {
                continue;
            }
            consumed = request;
            break;
        }
        if (consumed == null) {
            throw new BizException("验证码不正确或已过期，请联系管理员重新获取");
        }
        if (consumed.failCount.get() >= Constants.RESET_CODE_MAX_FAILS) {
            throw new BizException("验证码错误次数过多，申请已作废，请联系管理员重新处理");
        }
        boolean equals = MessageDigest.isEqual(
                consumed.code.getBytes(StandardCharsets.UTF_8), code.getBytes(StandardCharsets.UTF_8));
        if (!equals) {
            int fails = consumed.failCount.incrementAndGet();
            // 未达上限则放回，允许继续尝试（仍受剩余有效期约束）
            if (fails < Constants.RESET_CODE_MAX_FAILS) {
                REQUESTS.put(consumed.id, consumed);
                throw new BizException("验证码不正确，请核对后重试");
            }
            consumed.status = STATUS_EXPIRED;
            throw new BizException("验证码错误次数过多，申请已作废，请联系管理员重新处理");
        }
    }

    /** 清理已终结（作废/过期/超时未处理）的申请 */
    private static void prune(long now) {
        for (Request request : REQUESTS.values()) {
            boolean requestExpired = now - request.createdAtMillis > Constants.RESET_REQUEST_TTL_MINUTES * 60 * 1000L;
            boolean codeExpired = STATUS_CODE_ISSUED.equals(request.status) && request.codeExpiry <= now;
            if (requestExpired || codeExpired) {
                request.status = STATUS_EXPIRED;
            }
            if (STATUS_EXPIRED.equals(request.status) || STATUS_REJECTED.equals(request.status)) {
                REQUESTS.remove(request.id);
            }
        }
    }
}
