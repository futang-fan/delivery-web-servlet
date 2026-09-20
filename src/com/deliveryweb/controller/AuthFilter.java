package com.deliveryweb.controller;

import com.deliveryweb.dao.UserDao;
import com.deliveryweb.pojo.User;
import com.deliveryweb.util.Constants;
import com.deliveryweb.util.JsonUtil;
import com.deliveryweb.util.MyBatisUtil;
import com.deliveryweb.util.Result;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * API 鉴权过滤器：/api/auth/* 公开放行，其余 /api/* 一律要求登录（fail-closed），
 * 再按路径前缀做角色白名单校验；未登录或会话用户已被禁用返回真实 HTTP 401，角色不匹配返回 403，
 * 两者都携带标准 JSON 响应体。店铺归属、订单归属等细分权限由 Service 层校验，
 * 过滤器只做粗粒度的登录态与角色拦截。
 */
@WebFilter(filterName = "AuthFilter", urlPatterns = "/api/*")
public class AuthFilter implements Filter {

    /** 路径前缀 -> 允许角色白名单；未命中规则的前缀仅要求登录、不限角色。新增 Servlet 时在此补规则 */
    private static final Map<String, Set<String>> ROLE_RULES;

    static {
        Map<String, Set<String>> rules = new HashMap<>();
        rules.put("/api/admin/", Collections.singleton(Constants.ROLE_ADMIN));
        ROLE_RULES = Collections.unmodifiableMap(rules);
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String path = req.getRequestURI().substring(req.getContextPath().length());

        if (path.startsWith("/api/auth/")) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = req.getSession(false);
        User user = session == null
                ? null
                : (User) session.getAttribute(Constants.SESSION_LOGIN_USER);
        if (user == null) {
            res.setStatus(401);
            JsonUtil.writeJson(res, Result.fail("未登录或登录已过期"));
            return;
        }

        User latest = MyBatisUtil.withMapper(UserDao.class, dao -> dao.findById(user.getId()));
        if (latest == null || Constants.USER_STATUS_DISABLED.equals(latest.getStatus())) {
            session.invalidate();
            res.setStatus(401);
            JsonUtil.writeJson(res, Result.fail("该账号已被禁用，请联系平台管理员"));
            return;
        }

        Set<String> roles = matchRoles(path);
        if (roles != null && !roles.contains(user.getRole())) {
            res.setStatus(403);
            JsonUtil.writeJson(res, Result.fail("无权访问该资源"));
            return;
        }

        chain.doFilter(request, response);
    }

    /** 最长前缀匹配角色规则；无命中返回 null 表示不限角色 */
    private Set<String> matchRoles(String path) {
        Set<String> matched = null;
        int bestLength = 0;
        for (Map.Entry<String, Set<String>> rule : ROLE_RULES.entrySet()) {
            if (path.startsWith(rule.getKey()) && rule.getKey().length() > bestLength) {
                bestLength = rule.getKey().length();
                matched = rule.getValue();
            }
        }
        return matched;
    }

    @Override
    public void destroy() {
    }
}
