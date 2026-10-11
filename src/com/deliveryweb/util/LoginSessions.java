package com.deliveryweb.util;

import com.deliveryweb.dao.UserDao;
import com.deliveryweb.pojo.User;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 单 Tomcat 实例的登录会话管理；保留现有 Session 设计，不存储密码或密码摘要。 */
public final class LoginSessions {
    private static final String STAMP_KEY = "LOGIN_SESSION_STAMP";
    private static final ConcurrentHashMap<Long, Account> ACCOUNTS = new ConcurrentHashMap<>();

    private LoginSessions() { }

    private static final class Account {
        private volatile String version = UUID.randomUUID().toString();
        private final Set<HttpSession> sessions = ConcurrentHashMap.newKeySet();
    }

    private static final class Stamp implements HttpSessionBindingListener, Serializable {
        private static final long serialVersionUID = 1L;
        private final Long userId;
        private final String version;

        private Stamp(Long userId, String version) {
            this.userId = userId;
            this.version = version;
        }

        @Override
        public void valueBound(HttpSessionBindingEvent event) {
            Account account = ACCOUNTS.get(userId);
            if (account != null) account.sessions.add(event.getSession());
        }

        @Override
        public void valueUnbound(HttpSessionBindingEvent event) {
            Account account = ACCOUNTS.get(userId);
            if (account != null) account.sessions.remove(event.getSession());
        }
    }

    /** 认证成功后建立全新会话；再次查库，防止旧密码认证与禁用/改密交错后建立有效会话。 */
    public static User signIn(HttpServletRequest request, User authenticated) {
        Account account = ACCOUNTS.computeIfAbsent(authenticated.getId(), id -> new Account());
        synchronized (account) {
            User latest = MyBatisUtil.withMapper(UserDao.class, dao -> dao.findById(authenticated.getId()));
            if (latest == null || !Constants.USER_STATUS_NORMAL.equals(latest.getStatus())
                    || !authenticated.getPassword().equals(latest.getPassword())) {
                throw new BizException("账号状态或密码已变化，请重新登录");
            }
            invalidate(request.getSession(false));
            HttpSession session = request.getSession(true);
            latest.setPassword(null);
            session.setAttribute(Constants.SESSION_LOGIN_USER, latest);
            session.setAttribute(STAMP_KEY, new Stamp(latest.getId(), account.version));
            session.setMaxInactiveInterval(30 * 60);
            return latest;
        }
    }

    /** API、账户查询及页面入口共用的检查；刷新用户资料时保留原认证版本。 */
    public static User validUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        try {
            User user = (User) session.getAttribute(Constants.SESSION_LOGIN_USER);
            Stamp stamp = (Stamp) session.getAttribute(STAMP_KEY);
            if (user == null || stamp == null || !user.getId().equals(stamp.userId)) {
                invalidate(session);
                return null;
            }
            Account account = ACCOUNTS.get(user.getId());
            if (account == null || !stamp.version.equals(account.version)) {
                invalidate(session);
                return null;
            }
            User latest = MyBatisUtil.withMapper(UserDao.class, dao -> dao.findById(user.getId()));
            if (latest == null || !Constants.USER_STATUS_NORMAL.equals(latest.getStatus())
                    || !stamp.version.equals(account.version)) {
                invalidate(session);
                return null;
            }
            latest.setPassword(null);
            // 仅更新已有会话，不创建会话、不重新签发版本，避免资料保存复活旧登录态。
            session.setAttribute(Constants.SESSION_LOGIN_USER, latest);
            return latest;
        } catch (IllegalStateException expired) {
            return null;
        }
    }

    /** 禁用/改密事务提交后撤销全部会话；先标记版本，再在锁外销毁，避免容器回调锁冲突。 */
    public static void revoke(Long userId) {
        Account account = ACCOUNTS.get(userId);
        if (account == null) return;
        ArrayList<HttpSession> oldSessions;
        synchronized (account) {
            account.version = UUID.randomUUID().toString();
            oldSessions = new ArrayList<>(account.sessions);
            account.sessions.clear();
        }
        for (HttpSession session : oldSessions) invalidate(session);
    }

    /** 并发请求可能已销毁同一会话，重复销毁视为成功。 */
    public static void invalidate(HttpSession session) {
        if (session == null) return;
        try { session.invalidate(); }
        catch (IllegalStateException alreadyInvalid) { /* 会话已失效 */ }
    }
}
