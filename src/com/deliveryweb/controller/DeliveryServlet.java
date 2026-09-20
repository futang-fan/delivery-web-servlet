package com.deliveryweb.controller;

import com.deliveryweb.pojo.DeliveryVO;
import com.deliveryweb.pojo.User;
import com.deliveryweb.service.DeliveryService;
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
 * 配送接口：/api/delivery/pool|grab|node|remark|my|detail
 * 配送池/抢单/履约节点/配送说明/本人记录为骑手概念，角色 RIDER 在此校验；
 * 配送详情的归属校验（本人/本店/承接骑手/管理员）在 DeliveryService 内完成。
 * 异常兜底与路由工具由 {@link BaseApiServlet} 统一提供。
 */
@WebServlet("/api/delivery/*")
public class DeliveryServlet extends BaseApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected String prefix() {
        return "/api/delivery";
    }

    @Override
    protected void dispatch(String path, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        User user = currentUser(request);
        if ("GET".equals(request.getMethod())) {
            if ("/pool".equals(path)) {
                if (!requireRole(user, Constants.ROLE_RIDER, "仅骑手可操作配送业务", response)) {
                    return;
                }
                Page<DeliveryVO> page = DeliveryService.pool(
                        Page.of(intParam(request, "page"), intParam(request, "size")));
                JsonUtil.writeJson(response, Result.ok(page));
                return;
            }
            if ("/my".equals(path)) {
                if (!requireRole(user, Constants.ROLE_RIDER, "仅骑手可操作配送业务", response)) {
                    return;
                }
                Page<DeliveryVO> page = DeliveryService.my(user.getId(), request.getParameter("status"),
                        Page.of(intParam(request, "page"), intParam(request, "size")));
                JsonUtil.writeJson(response, Result.ok(page));
                return;
            }
            if ("/stats".equals(path)) {
                if (!requireRole(user, Constants.ROLE_RIDER, "仅骑手可操作配送业务", response)) {
                    return;
                }
                JsonUtil.writeJson(response, Result.ok(DeliveryService.riderStats(user.getId())));
                return;
            }
            if ("/detail".equals(path)) {
                DeliveryVO vo = DeliveryService.detail(user.getRole(), user.getId(),
                        longParam(request, "orderId"));
                JsonUtil.writeJson(response, Result.ok(vo));
                return;
            }
            JsonUtil.writeJson(response, Result.fail("接口不存在"));
            return;
        }
        if (!requireRole(user, Constants.ROLE_RIDER, "仅骑手可操作配送业务", response)) {
            return;
        }
        Map<String, Object> body = JsonUtil.parseBody(request);
        switch (path) {
            case "/grab": {
                Long grabbed = DeliveryService.grab(user.getId(),
                        JsonUtil.longValue(body, "deliveryId"));
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("deliveryId", grabbed);
                JsonUtil.writeJson(response, Result.ok("抢单成功", data));
                break;
            }
            case "/node": {
                DeliveryService.node(user.getId(), JsonUtil.longValue(body, "deliveryId"),
                        JsonUtil.str(body, "status"));
                JsonUtil.writeJson(response, Result.ok("履约节点已更新", null));
                break;
            }
            case "/remark": {
                DeliveryService.remark(user.getId(), JsonUtil.longValue(body, "deliveryId"),
                        JsonUtil.str(body, "remark"));
                JsonUtil.writeJson(response, Result.ok("配送说明已反馈", null));
                break;
            }
            default:
                JsonUtil.writeJson(response, Result.fail("接口不存在"));
        }
    }
}
