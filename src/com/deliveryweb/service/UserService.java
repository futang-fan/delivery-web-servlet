package com.deliveryweb.service;

import com.deliveryweb.dao.ShopDao;
import com.deliveryweb.dao.UserDao;
import com.deliveryweb.pojo.Shop;
import com.deliveryweb.pojo.User;
import com.deliveryweb.util.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 用户业务逻辑：注册唯一性/格式校验、登录校验。
 */
public final class UserService {

    /** 用户名：4-16 位字母、数字或下划线（表2.2） */
    private static final java.util.regex.Pattern USERNAME_PATTERN = java.util.regex.Pattern.compile("^[A-Za-z0-9_]{4,16}$");

    /** 开放注册的角色：仅顾客和商家，骑手由管理员创建（表2.2） */
    private static final Set<String> REGISTER_ROLES = new HashSet<>(
            Arrays.asList(Constants.ROLE_CUSTOMER, Constants.ROLE_SHOP));

    private UserService() {
    }

    /**
     * 注册：校验格式与唯一性，密码以“盐:摘要”存储。
     * 商家注册成功后自动生成"待审核"店铺占位记录，
     * 入驻资料由 ShopService.apply 填写，与用户写入同处一个事务。
     */
    public static void register(String username, String password, String phone, String role) {
        String name = JsonUtil.trimToNull(username);
        String mobile = JsonUtil.trimToNull(phone);
        String targetRole = JsonUtil.trimToNull(role);

        if (name == null || !USERNAME_PATTERN.matcher(name).matches()) {
            throw new BizException("用户名须为 4-16 位字母、数字或下划线");
        }
        if (password == null || password.length() < 6) {
            throw new BizException("密码长度不能少于 6 位");
        }
        if (mobile == null || !Constants.PHONE_PATTERN.matcher(mobile).matches()) {
            throw new BizException("手机号须为 11 位数字");
        }
        if (targetRole == null || !REGISTER_ROLES.contains(targetRole)) {
            throw new BizException("注册角色仅支持顾客或商家");
        }
        if (findByUsername(name) != null) {
            throw new BizException("用户名已存在");
        }
        if (findByPhone(mobile) != null) {
            throw new BizException("手机号已被注册");
        }

        User user = new User();
        user.setUsername(name);
        user.setPassword(PasswordUtil.encode(password));
        user.setRole(targetRole);
        user.setPhone(mobile);
        user.setStatus(Constants.USER_STATUS_NORMAL);
        user.setCreatedAt(LocalDateTime.now());

        MyBatisUtil.withTx(session -> {
            session.getMapper(UserDao.class).insert(user);
            if (Constants.ROLE_SHOP.equals(targetRole)) {
                Shop shop = new Shop();
                shop.setOwnerId(user.getId());
                shop.setName("");
                shop.setAuditStatus(Constants.SHOP_AUDIT_PENDING);
                shop.setBusinessStatus(Constants.BUSINESS_CLOSED);
                shop.setCreatedAt(LocalDateTime.now());
                session.getMapper(ShopDao.class).insert(shop);
            }
            return null;
        });
    }

    /**
     * 登录校验：用户名不存在、密码错误统一提示；
     * 被禁用账号不能登录；同一用户名 10 分钟内连续失败 5 次锁定 10 分钟（防暴力破解）。
     */
    public static User login(String username, String password) {
        String name = JsonUtil.trimToNull(username);
        if (name == null || password == null || password.isEmpty()) {
            throw new BizException("请输入用户名和密码");
        }
        long now = System.currentTimeMillis();
        long[] rec = LOGIN_FAILS.get(name);
        if (rec != null && rec[2] > now) {
            throw new BizException("密码错误次数过多，请 10 分钟后再试");
        }

        User user = findByUsername(name);
        if (user == null || !PasswordUtil.verify(password, user.getPassword())) {
            recordLoginFail(name, now);
            throw new BizException("用户名或密码错误");
        }
        if (Constants.USER_STATUS_DISABLED.equals(user.getStatus())) {
            throw new BizException("该账号已被禁用，请联系平台管理员");
        }
        LOGIN_FAILS.remove(name);
        return user;
    }

    /** 登录失败计数：同一用户名 10 分钟窗口内累计，达 5 次设置 lockUntil */
    private static void recordLoginFail(String name, long now) {
        LOGIN_FAILS.compute(name, (key, old) -> {
            long[] cur = (old == null || now - old[1] > LOGIN_WINDOW_MILLIS) ? new long[]{0, now, 0} : old;
            cur[0]++;
            if (cur[0] >= LOGIN_FAIL_MAX) {
                cur[2] = now + LOGIN_WINDOW_MILLIS;
            }
            return cur;
        });
    }

    /** 登录防暴破状态：{失败次数, 窗口起点, 锁定截止}；内存级实现 */
    private static final int LOGIN_FAIL_MAX = 5;
    private static final long LOGIN_WINDOW_MILLIS = 10 * 60 * 1000L;
    private static final java.util.Map<String, long[]> LOGIN_FAILS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 修改密码：校验旧密码后更新摘要（账户信息维护）。
     * 成功后由调用方销毁当前 Session 强制重新登录。
     */
    public static void changePassword(Long userId, String oldPassword, String newPassword) {
        if (userId == null) {
            throw new BizException("未登录或登录已过期");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new BizException("密码长度不能少于 6 位");
        }
        User user = MyBatisUtil.withMapper(UserDao.class, dao -> dao.findById(userId));
        if (user == null) {
            throw new BizException("用户不存在");
        }
        if (!PasswordUtil.verify(oldPassword, user.getPassword())) {
            throw new BizException("旧密码错误");
        }
        MyBatisUtil.withTx(tx -> tx.getMapper(UserDao.class)
                .updatePassword(userId, PasswordUtil.encode(newPassword)));
    }

    /**
     * 忘记密码第一步：提交找回申请（管理员协助）。
     * 用户名与注册手机号匹配才受理（不匹配统一提示，防止账号枚举）；
     * 申请与验证码存内存（ResetCodeStore，无数据库表），由管理员在"找回审批"页签核实后下发一次性验证码。
     */
    public static void requestPasswordReset(String username, String phone) {
        String name = JsonUtil.trimToNull(username);
        String mobile = JsonUtil.trimToNull(phone);
        if (name == null || mobile == null || !Constants.PHONE_PATTERN.matcher(mobile).matches()) {
            throw new BizException("请输入用户名与 11 位注册手机号");
        }
        User user = findByUsername(name);
        if (user == null || !mobile.equals(user.getPhone())) {
            throw new BizException("用户名与注册手机号不匹配");
        }
        ResetCodeStore.create(name, maskPhone(mobile));
    }

    /**
     * 忘记密码第二步：凭管理员下发的一次性验证码重置密码。
     * 验证码绑定用户名、一次性使用（ResetCodeStore.verifyAndConsume），通过后走原重置逻辑。
     */
    public static void resetPassword(String username, String code, String newPassword) {
        String name = JsonUtil.trimToNull(username);
        if (name == null) {
            throw new BizException("请输入用户名");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new BizException("密码长度不能少于 6 位");
        }
        if (code == null || code.trim().isEmpty()) {
            throw new BizException("请输入管理员下发的验证码");
        }
        ResetCodeStore.verifyAndConsume(name, code.trim());
        User user = findByUsername(name);
        if (user == null) {
            throw new BizException("账号不存在");
        }
        MyBatisUtil.withTx(tx -> tx.getMapper(UserDao.class)
                .updatePassword(user.getId(), PasswordUtil.encode(newPassword)));
    }

    /** 手机号脱敏展示：138****0003（管理端找回审批列表用） */
    private static String maskPhone(String phone) {
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /**
     * 修改手机号（账户信息维护）：校验 11 位 + 未被其他账号占用；
     * 返回更新后的用户供调用方刷新 Session（改手机号不强制重登）。
     */
    public static User updateProfile(Long userId, String phone) {
        String mobile = JsonUtil.trimToNull(phone);
        if (mobile == null || !Constants.PHONE_PATTERN.matcher(mobile).matches()) {
            throw new BizException("手机号须为 11 位数字");
        }
        User user = MyBatisUtil.withMapper(UserDao.class, dao -> dao.findById(userId));
        if (user == null) {
            throw new BizException("用户不存在");
        }
        if (mobile.equals(user.getPhone())) {
            return user;
        }
        User occupied = findByPhone(mobile);
        if (occupied != null && !occupied.getId().equals(userId)) {
            throw new BizException("手机号已被其他账号占用");
        }
        MyBatisUtil.withTx(tx -> tx.getMapper(UserDao.class).updatePhone(userId, mobile));
        user.setPhone(mobile);
        return user;
    }

    private static User findByUsername(String username) {
        return MyBatisUtil.withMapper(UserDao.class,
                dao -> dao.findByUsername(username));
    }

    /** 管理员用户分页：角色/状态可选筛选、关键字匹配用户名或手机号；密码摘要脱敏后返回 */
    public static Page<User> listForAdmin(String role, String status, String keyword, Page<User> page) {
        page.setTotal(MyBatisUtil.withMapper(UserDao.class,
                dao -> dao.countByScope(role, status, keyword)));
        if (page.getTotal() > 0) {
            page.setRecords(MyBatisUtil.withMapper(UserDao.class,
                    dao -> dao.listByScope(role, status, keyword, page.offset(), page.getSize())));
        }
        for (User user : page.getRecords()) {
            user.setPassword(null);
        }
        return page;
    }

    /** 禁用/启用账号：被禁用账号不能登录；不能禁用自己 */
    public static void changeStatus(Long operatorId, Long userId, String status) {
        if (userId == null) {
            throw new BizException("缺少用户参数");
        }
        if (!Constants.USER_STATUS_NORMAL.equals(status) && !Constants.USER_STATUS_DISABLED.equals(status)) {
            throw new BizException("无效的状态：" + status);
        }
        if (userId.equals(operatorId)) {
            throw new BizException("不能禁用当前登录的账号");
        }
        User target = MyBatisUtil.withMapper(UserDao.class, dao -> dao.findById(userId));
        if (target == null) {
            throw new BizException("用户不存在");
        }
        MyBatisUtil.withTx(tx -> tx.getMapper(UserDao.class).updateStatus(userId, status));
    }

    /** 创建骑手账号：角色固定 RIDER，不开放注册；校验口径与注册一致 */
    public static Long createRider(String username, String password, String phone) {
        String name = JsonUtil.trimToNull(username);
        String mobile = JsonUtil.trimToNull(phone);
        if (name == null || !USERNAME_PATTERN.matcher(name).matches()) {
            throw new BizException("用户名须为 4-16 位字母、数字或下划线");
        }
        if (password == null || password.length() < 6) {
            throw new BizException("密码长度不能少于 6 位");
        }
        if (mobile == null || !Constants.PHONE_PATTERN.matcher(mobile).matches()) {
            throw new BizException("手机号须为 11 位数字");
        }
        if (findByUsername(name) != null) {
            throw new BizException("用户名已存在");
        }
        if (findByPhone(mobile) != null) {
            throw new BizException("手机号已被注册");
        }
        User user = new User();
        user.setUsername(name);
        user.setPassword(PasswordUtil.encode(password));
        user.setRole(Constants.ROLE_RIDER);
        user.setPhone(mobile);
        user.setStatus(Constants.USER_STATUS_NORMAL);
        user.setCreatedAt(LocalDateTime.now());
        MyBatisUtil.withTx(tx -> tx.getMapper(UserDao.class).insert(user));
        return user.getId();
    }

    private static User findByPhone(String phone) {
        return MyBatisUtil.withMapper(UserDao.class,
                dao -> dao.findByPhone(phone));
    }
}
