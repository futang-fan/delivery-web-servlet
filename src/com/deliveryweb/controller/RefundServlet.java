package com.deliveryweb.controller;

import com.deliveryweb.pojo.RefundVO;
import com.deliveryweb.pojo.User;
import com.deliveryweb.service.RefundService;
import com.deliveryweb.util.Constants;
import com.deliveryweb.util.JsonUtil;
import com.deliveryweb.util.Page;
import com.deliveryweb.util.Result;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * 退款接口：/api/refund/apply|handle|pending
 * 申请为登录用户（归属校验在 Service）；处理与待处理列表为商家概念，角色 SHOP 在此校验，
 * 本店归属校验在 RefundService 内完成。
 * 异常兜底与路由工具由 {@link BaseApiServlet} 统一提供。
 */
@WebServlet("/api/refund/*")
public class RefundServlet extends BaseApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected String prefix() {
        return "/api/refund";
    }

    @Override
    protected void dispatch(String path, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        User user = currentUser(request);
        if ("GET".equals(request.getMethod())) {
            if ("/pending".equals(path)) {
                if (!requireRole(user, Constants.ROLE_SHOP, "仅商家可处理退款", response)) {
                    return;
                }
                Page<RefundVO> page = RefundService.pending(user.getId(),
                        Page.of(intParam(request, "page"), intParam(request, "size")));
                JsonUtil.writeJson(response, Result.ok(page));
                return;
            }
            JsonUtil.writeJson(response, Result.fail("接口不存在"));
            return;
        }
        Map<String, Object> body = JsonUtil.parseBody(request);
        switch (path) {
            case "/apply": {
                RefundService.apply(user.getId(), JsonUtil.longValue(body, "orderId"),
                        JsonUtil.str(body, "reason"));
                JsonUtil.writeJson(response, Result.ok("退款申请已提交，等待商家处理", null));
                break;
            }
            case "/handle": {
                if (!requireRole(user, Constants.ROLE_SHOP, "仅商家可处理退款", response)) {
                    return;
                }
                RefundService.handle(user.getId(), JsonUtil.longValue(body, "refundId"),
                        JsonUtil.isTrue(body.get("agree")), JsonUtil.str(body, "remark"));
                JsonUtil.writeJson(response, Result.ok("退款已处理", null));
                break;
            }
            default:
                JsonUtil.writeJson(response, Result.fail("接口不存在"));
        }
    }
}
