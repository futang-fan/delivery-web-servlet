package com.deliveryweb.controller;

import com.deliveryweb.pojo.User;
import com.deliveryweb.util.BizException;
import com.deliveryweb.util.Constants;
import com.deliveryweb.util.JsonUtil;
import com.deliveryweb.util.Result;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * API Servlet 基类：统一路由入口、统一异常兜底与公共工具方法。
 * 子类仅实现 {@link #dispatch} 与 {@link #prefix()}；基类保证：
 * BizException → HTTP 200 + 业务 fail(msg)；其他异常 → 记日志 + 「系统繁忙」fail；
 * 角色校验统一走 {@link #requireRole}（不匹配写 HTTP 403 + fail，复刻原各 Servlet requireXxx 语义，
 * 测试对 403 状态码的断言依赖此行为，不得改为 200）。
 */
public abstract class BaseApiServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected final void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        handle(request, response);
    }

    @Override
    protected final void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        handle(request, response);
    }

    private void handle(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = path(request);
        try {
            dispatch(path, request, response);
        } catch (BizException e) {
            writeFail(response, e.getMessage());
        } catch (Exception e) {
            log(getClass().getSimpleName() + " 接口处理异常", e);
            writeFail(response, "系统繁忙，请稍后重试");
        }
    }

    private void writeFail(HttpServletResponse response, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        JsonUtil.writeJson(response, Result.fail(message));
    }

    protected abstract void dispatch(String path, HttpServletRequest request, HttpServletResponse response)
            throws Exception;

    protected abstract String prefix();

    protected String path(HttpServletRequest request) {
        String pathInfo = request.getPathInfo();
        if (pathInfo != null) {
            return pathInfo;
        }
        String uri = request.getRequestURI().substring(request.getContextPath().length());
        return uri.substring(prefix().length());
    }

    protected User currentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        return (User) session.getAttribute(Constants.SESSION_LOGIN_USER);
    }
    protected Integer intParam(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected Long longParam(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected boolean requireRole(User user, String role, String message, HttpServletResponse response)
            throws IOException {
        if (user == null || !role.equals(user.getRole())) {
            response.setStatus(403);
            JsonUtil.writeJson(response, Result.fail(message));
            return false;
        }
        return true;
    }
}
