package com.deliveryweb.controller;

import com.deliveryweb.service.CartService;
import com.deliveryweb.util.Constants;
import com.deliveryweb.util.JsonUtil;
import com.deliveryweb.util.Result;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;

/**
 * 购物车接口：/api/cart/list、/add、/count、/remove、/clear（顾客，Session 存储）。
 * 异常兜底与路由工具由 {@link BaseApiServlet} 统一提供。
 */
@WebServlet("/api/cart/*")
public class CartServlet extends BaseApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected String prefix() {
        return "/api/cart";
    }

    @Override
    protected void dispatch(String path, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        if (!requireRole(currentUser(request), Constants.ROLE_CUSTOMER, "仅顾客可操作购物车", response)) {
            return;
        }
        if ("GET".equals(request.getMethod())) {
            if ("/list".equals(path)) {
                JsonUtil.writeJson(response, Result.ok(CartService.list(request.getSession(false))));
                return;
            }
            JsonUtil.writeJson(response, Result.fail("接口不存在"));
            return;
        }
        HttpSession session = request.getSession(false);
        Map<String, Object> body = JsonUtil.parseBody(request);
        switch (path) {
            case "/add":
                CartService.add(session, JsonUtil.longValue(body, "dishId"), JsonUtil.num(body, "quantity"));
                JsonUtil.writeJson(response, Result.ok("已加入购物车", null));
                break;
            case "/count":
                CartService.updateCount(session, JsonUtil.longValue(body, "dishId"), JsonUtil.num(body, "quantity"));
                JsonUtil.writeJson(response, Result.ok("已更新数量", null));
                break;
            case "/remove":
                CartService.remove(session, JsonUtil.longValue(body, "dishId"));
                JsonUtil.writeJson(response, Result.ok("已移除", null));
                break;
            case "/clear":
                CartService.clear(session);
                JsonUtil.writeJson(response, Result.ok("购物车已清空", null));
                break;
            default:
                JsonUtil.writeJson(response, Result.fail("接口不存在"));
        }
    }

}
