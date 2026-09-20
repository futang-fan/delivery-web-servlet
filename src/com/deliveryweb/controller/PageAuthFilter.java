package com.deliveryweb.controller;

import com.deliveryweb.pojo.User;
import com.deliveryweb.util.Constants;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * 页面级登录态过滤器（P1-5 纵深防御）：/pages/* 下除登录页外一律要求已登录，
 * 未登录直接 302 回登录页，避免未登录用户敲深链看到"空壳页面再被前端跳走"的闪烁。
 * 数据面安全仍由 {@link AuthFilter}（/api/* fail-closed）保证，本过滤器只兜底页面骨架；
 * 角色细分仍由前端 requireRole 与各 Servlet 的 requireRole 承担，此处不判角色。
 * 注意白名单必须包含 login.html（它本身在 /pages/ 下且是欢迎页目标），否则未登录即重定向循环。
 */
@WebFilter(filterName = "PageAuthFilter", urlPatterns = "/pages/*")
public class PageAuthFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String path = req.getRequestURI().substring(req.getContextPath().length());
        if ("/pages/login.html".equals(path)) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = req.getSession(false);
        User user = session == null
                ? null
                : (User) session.getAttribute(Constants.SESSION_LOGIN_USER);
        if (user == null) {
            res.sendRedirect(req.getContextPath() + "/pages/login.html");
            return;
        }
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}
