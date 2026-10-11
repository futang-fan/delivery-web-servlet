package com.deliveryweb.controller;

import com.deliveryweb.pojo.User;
import com.deliveryweb.util.Constants;
import com.deliveryweb.util.JsonUtil;
import com.deliveryweb.util.LoginSessions;
import com.deliveryweb.util.Result;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * API 鉴权过滤器：仅明确的公开账户接口放行，其余 /api/* 一律要求有效登录，
 * 再按路径前缀做角色白名单校验；未登录或会话用户已被禁用返回真实 HTTP 401，角色不匹配返回 403，
 * 两者都携带标准 JSON 响应体。店铺归属、订单归属等细分权限由 Service 层校验，
 * 过滤器只做粗粒度的登录态与角色拦截。
 */
@WebFilter(filterName = "AuthFilter", urlPatterns = "/api/*")
public class AuthFilter implements Filter {

    private static final Set<String> PUBLIC_POST_PATHS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "/api/auth/login", "/api/auth/register", "/api/auth/logout",
            "/api/auth/reset-request", "/api/auth/reset-password")));

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

        if ("POST".equals(req.getMethod()) && PUBLIC_POST_PATHS.contains(path)) {
            chain.doFilter(request, response);
            return;
        }
        User user = LoginSessions.validUser(req);
        // 保留 current 的原约定：匿名查询返回 HTTP 200 + 业务未登录，不返回过期用户资料。
        if ("GET".equals(req.getMethod()) && "/api/auth/current".equals(path)) {
            chain.doFilter(request, response);
            return;
        }
        if (user == null) {
            res.setStatus(401);
            JsonUtil.writeJson(res, Result.fail("未登录或登录已过期"));
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
