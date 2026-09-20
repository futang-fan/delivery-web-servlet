package com.deliveryweb.controller;

import com.deliveryweb.pojo.User;
import com.deliveryweb.service.UserService;
import com.deliveryweb.util.Constants;
import com.deliveryweb.util.JsonUtil;
import com.deliveryweb.util.Result;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 账户接口：/api/auth/login、/api/auth/register、/api/auth/logout、/api/auth/current
 * 异常兜底与路由工具由 {@link BaseApiServlet} 统一提供（原 doGet 无兜底，迁移后补齐）。
 */
@WebServlet("/api/auth/*")
public class AuthServlet extends BaseApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected String prefix() {
        return "/api/auth";
    }

    @Override
    protected void dispatch(String path, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        if ("GET".equals(request.getMethod())) {
            if ("/current".equals(path)) {
                handleCurrent(request, response);
                return;
            }
            JsonUtil.writeJson(response, Result.fail("接口不存在"));
            return;
        }
        switch (path) {
            case "/login":
                handleLogin(request, response);
                break;
            case "/register":
                handleRegister(request, response);
                break;
            case "/logout":
                handleLogout(request, response);
                break;
            case "/change-password":
                handleChangePassword(request, response);
                break;
            case "/reset-request":
                handleResetRequest(request, response);
                break;
            case "/reset-password":
                handleResetPassword(request, response);
                break;
            case "/update-profile":
                handleUpdateProfile(request, response);
                break;
            default:
                JsonUtil.writeJson(response, Result.fail("接口不存在"));
        }
    }

    private void handleLogin(HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        Map<String, Object> body = JsonUtil.parseBody(request);
        String username = JsonUtil.str(body, "username");
        String password = body.get("password") == null
                ? null
                : String.valueOf(body.get("password"));

        User user = UserService.login(username, password);
        // 会话固定防护：存在旧会话时先更换 Session ID 再写入登录态（首登无会话则直接新建）
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        saveLoginSession(request, user);
        JsonUtil.writeJson(response, Result.ok("登录成功", toUserMap(user)));
    }

    private void handleRegister(HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        Map<String, Object> body = JsonUtil.parseBody(request);
        Object rawPassword = body.get("password");
        UserService.register(
                JsonUtil.str(body, "username"),
                rawPassword == null ? null : String.valueOf(rawPassword),
                JsonUtil.str(body, "phone"),
                JsonUtil.str(body, "role"));
        JsonUtil.writeJson(response, Result.ok("注册成功", null));
    }

    private void handleLogout(HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        JsonUtil.writeJson(response, Result.ok("已退出登录", null));
    }

    private void handleCurrent(HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        User user = currentUser(request);
        if (user == null) {
            JsonUtil.writeJson(response, Result.fail("未登录"));
            return;
        }
        JsonUtil.writeJson(response, Result.ok(toUserMap(user)));
    }

    /**
     * 修改密码：/api/auth/* 被 AuthFilter 放行，登录态在此校验；
     * 成功后销毁当前 Session 强制重新登录。
     */
    private void handleChangePassword(HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        User user = currentUser(request);
        HttpSession session = request.getSession(false);
        if (user == null) {
            JsonUtil.writeJson(response, Result.fail("未登录或登录已过期"));
            return;
        }
        Map<String, Object> body = JsonUtil.parseBody(request);
        Object rawOld = body.get("oldPassword");
        Object rawNew = body.get("newPassword");
        UserService.changePassword(
                user.getId(),
                rawOld == null ? null : String.valueOf(rawOld),
                rawNew == null ? null : String.valueOf(rawNew));
        session.invalidate();
        JsonUtil.writeJson(response, Result.ok("密码修改成功，请重新登录", null));
    }

    /**
     * 忘记密码第一步：提交找回申请（公开接口）。
     * 用户名与注册手机号匹配才受理，管理员在"找回审批"页签核实后下发一次性验证码。
     */
    private void handleResetRequest(HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        Map<String, Object> body = JsonUtil.parseBody(request);
        UserService.requestPasswordReset(
                JsonUtil.str(body, "username"),
                JsonUtil.str(body, "phone"));
        JsonUtil.writeJson(response, Result.ok("申请已提交，请线下联系管理员获取验证码", null));
    }

    /**
     * 忘记密码第二步：凭管理员下发的一次性验证码重置（公开接口）；成功后销毁当前 Session（若存在）。
     */
    private void handleResetPassword(HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        Map<String, Object> body = JsonUtil.parseBody(request);
        Object rawNew = body.get("newPassword");
        UserService.resetPassword(
                JsonUtil.str(body, "username"),
                JsonUtil.str(body, "code"),
                rawNew == null ? null : String.valueOf(rawNew));
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        JsonUtil.writeJson(response, Result.ok("密码重置成功，请重新登录", null));
    }

    /**
     * 修改手机号：/api/auth/* 被 AuthFilter 放行，登录态在此校验；
     * 成功后同步刷新 Session 内用户手机号（改手机号不强制重登）。
     */
    private void handleUpdateProfile(HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        User user = currentUser(request);
        if (user == null) {
            JsonUtil.writeJson(response, Result.fail("未登录或登录已过期"));
            return;
        }
        Map<String, Object> body = JsonUtil.parseBody(request);
        User updated = UserService.updateProfile(user.getId(), JsonUtil.str(body, "phone"));
        saveLoginSession(request, updated);
        JsonUtil.writeJson(response, Result.ok("手机号修改成功", toUserMap(updated)));
    }

    /**
     * 将登录用户写入 Session；Session 中不保存密码等敏感信息。
     */
    private void saveLoginSession(HttpServletRequest request, User source) {
        User safeUser = new User();
        safeUser.setId(source.getId());
        safeUser.setUsername(source.getUsername());
        safeUser.setRole(source.getRole());
        safeUser.setPhone(source.getPhone());
        safeUser.setStatus(source.getStatus());
        safeUser.setCreatedAt(source.getCreatedAt());

        HttpSession session = request.getSession(true);
        session.setAttribute(com.deliveryweb.util.Constants.SESSION_LOGIN_USER, safeUser);
        session.setMaxInactiveInterval(30 * 60);
    }

    /**
     * 返回给前端的安全用户信息（不含密码摘要）；orderTimeoutMinutes 为前端倒计时阈值单源下发，
     * 取代原 common.js 冗余常量（2026-09-12）。
     */
    private Map<String, Object> toUserMap(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("role", user.getRole());
        map.put("phone", user.getPhone());
        map.put("status", user.getStatus());
        map.put("createdAt", user.getCreatedAt());
        map.put("orderTimeoutMinutes", Constants.ORDER_TIMEOUT_MINUTES);
        return map;
    }
}
