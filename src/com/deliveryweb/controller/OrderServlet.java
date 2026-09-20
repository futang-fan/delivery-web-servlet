package com.deliveryweb.controller;

import com.deliveryweb.pojo.Order;
import com.deliveryweb.pojo.OrderDetailVO;
import com.deliveryweb.pojo.User;
import com.deliveryweb.service.OrderService;
import com.deliveryweb.util.Constants;
import com.deliveryweb.util.JsonUtil;
import com.deliveryweb.util.Page;
import com.deliveryweb.util.Result;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 订单接口：/api/order/token|create|pay|cancel|list|accept|reject|ready|receive|detail
 * 下单为顾客概念，角色 CUSTOMER 在此校验；接单/拒单/备餐为商家概念，角色 SHOP 在此校验；
 * 支付/取消/商家操作的归属校验在 OrderService 内完成。订单详情（detail）按排期 D8 交付。
 * 异常兜底与路由工具由 {@link BaseApiServlet} 统一提供。
 */
@WebServlet("/api/order/*")
public class OrderServlet extends BaseApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected String prefix() {
        return "/api/order";
    }

    @Override
    protected void dispatch(String path, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        if ("GET".equals(request.getMethod())) {
            dispatchGet(path, request, response);
            return;
        }
        dispatchPost(path, request, response);
    }

    private void dispatchGet(String path, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        if ("/token".equals(path)) {
            if (requireRole(currentUser(request), Constants.ROLE_CUSTOMER, "仅顾客可下单", response)) {
                String token = OrderService.issueToken(request.getSession(false));
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("token", token);
                JsonUtil.writeJson(response, Result.ok(data));
            }
            return;
        }
        if ("/list".equals(path)) {
            User user = currentUser(request);
            Page<Order> page = OrderService.listByRole(user.getRole(), user.getId(),
                    request.getParameter("status"),
                    request.getParameter("payStatus"),
                    Page.of(intParam(request, "page"), intParam(request, "size")));
            JsonUtil.writeJson(response, Result.ok(page));
            return;
        }
        if ("/detail".equals(path)) {
            User user = currentUser(request);
            OrderDetailVO vo = OrderService.detail(user.getRole(), user.getId(),
                    longParam(request, "orderId"));
            JsonUtil.writeJson(response, Result.ok(vo));
            return;
        }
        JsonUtil.writeJson(response, Result.fail("接口不存在"));
    }

    private void dispatchPost(String path, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        User user = currentUser(request);
        Map<String, Object> body = JsonUtil.parseBody(request);
        switch (path) {
            case "/create": {
                if (!requireRole(user, Constants.ROLE_CUSTOMER, "仅顾客可下单", response)) {
                    return;
                }
                Map<String, Object> data = OrderService.createOrder(request.getSession(false), user.getId(),
                        JsonUtil.longValue(body, "addressId"),
                        JsonUtil.str(body, "remark"),
                        JsonUtil.str(body, "token"));
                JsonUtil.writeJson(response, Result.ok("下单成功", data));
                break;
            }
            case "/pay": {
                OrderService.pay(user.getId(), JsonUtil.longValue(body, "orderId"));
                JsonUtil.writeJson(response, Result.ok("支付成功", null));
                break;
            }
            case "/cancel": {
                OrderService.cancel(user.getId(), JsonUtil.longValue(body, "orderId"));
                JsonUtil.writeJson(response, Result.ok("订单已取消", null));
                break;
            }
            case "/accept": {
                if (!requireRole(user, Constants.ROLE_SHOP, "仅商家可操作订单", response)) {
                    return;
                }
                OrderService.accept(user.getId(), JsonUtil.longValue(body, "orderId"));
                JsonUtil.writeJson(response, Result.ok("接单成功", null));
                break;
            }
            case "/reject": {
                if (!requireRole(user, Constants.ROLE_SHOP, "仅商家可操作订单", response)) {
                    return;
                }
                OrderService.reject(user.getId(), JsonUtil.longValue(body, "orderId"),
                        JsonUtil.str(body, "reason"));
                JsonUtil.writeJson(response, Result.ok("已拒单", null));
                break;
            }
            case "/ready": {
                if (!requireRole(user, Constants.ROLE_SHOP, "仅商家可操作订单", response)) {
                    return;
                }
                OrderService.ready(user.getId(), JsonUtil.longValue(body, "orderId"));
                JsonUtil.writeJson(response, Result.ok("备餐完成，订单已进入配送池", null));
                break;
            }
            case "/receive": {
                OrderService.receive(user.getId(), JsonUtil.longValue(body, "orderId"));
                JsonUtil.writeJson(response, Result.ok("已确认收货", null));
                break;
            }
            default:
                JsonUtil.writeJson(response, Result.fail("接口不存在"));
        }
    }
}
