package com.deliveryweb.controller;

import com.deliveryweb.pojo.Review;
import com.deliveryweb.pojo.User;
import com.deliveryweb.service.ReviewService;
import com.deliveryweb.util.JsonUtil;
import com.deliveryweb.util.Page;
import com.deliveryweb.util.Result;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 评价接口：/api/review/submit|by-shop|exists
 * 提交为登录用户（本人订单归属校验在 ReviewService）；查询类接口登录即可。
 * 异常兜底与路由工具由 {@link BaseApiServlet} 统一提供。
 *
 * @author DeliveryWeb
 */
@WebServlet("/api/review/*")
public class ReviewServlet extends BaseApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected String prefix() {
        return "/api/review";
    }

    @Override
    protected void dispatch(String path, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        if ("GET".equals(request.getMethod())) {
            if ("/by-shop".equals(path)) {
                Integer shopId = intParam(request, "shopId");
                Page<Review> page = ReviewService.byShop(shopId == null ? null : shopId.longValue(),
                        Page.of(intParam(request, "page"), intParam(request, "size")));
                JsonUtil.writeJson(response, Result.ok(page));
                return;
            }
            if ("/exists".equals(path)) {
                // P2-2 修复（2026-09-13）：评价状态查询补归属校验，禁止探测他人订单
                User user = currentUser(request);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("reviewed", ReviewService.exists(user.getId(), longParam(request, "orderId")));
                JsonUtil.writeJson(response, Result.ok(data));
                return;
            }
            JsonUtil.writeJson(response, Result.fail("接口不存在"));
            return;
        }
        User user = currentUser(request);
        Map<String, Object> body = JsonUtil.parseBody(request);
        switch (path) {
            case "/submit": {
                ReviewService.submit(user.getId(), JsonUtil.longValue(body, "orderId"),
                        JsonUtil.num(body, "rating"), JsonUtil.str(body, "content"));
                JsonUtil.writeJson(response, Result.ok("评价成功", null));
                break;
            }
            default:
                JsonUtil.writeJson(response, Result.fail("接口不存在"));
        }
    }
}
