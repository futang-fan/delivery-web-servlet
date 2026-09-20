package com.deliveryweb.controller;

import com.deliveryweb.pojo.User;
import com.deliveryweb.service.AddressService;
import com.deliveryweb.util.Constants;
import com.deliveryweb.util.JsonUtil;
import com.deliveryweb.util.Result;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * 收货地址接口：/api/address/list、/save、/delete、/set-default（顾客）。
 * AuthFilter 只保证登录态；地址为顾客概念，角色 CUSTOMER 在此校验。
 * 异常兜底与路由工具由 {@link BaseApiServlet} 统一提供。
 */
@WebServlet("/api/address/*")
public class AddressServlet extends BaseApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected String prefix() {
        return "/api/address";
    }

    @Override
    protected void dispatch(String path, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        if (!requireRole(currentUser(request), Constants.ROLE_CUSTOMER, "仅顾客可管理收货地址", response)) {
            return;
        }
        if ("GET".equals(request.getMethod())) {
            if ("/list".equals(path)) {
                JsonUtil.writeJson(response, Result.ok(AddressService.list(currentUser(request).getId())));
                return;
            }
            JsonUtil.writeJson(response, Result.fail("接口不存在"));
            return;
        }
        User user = currentUser(request);
        Map<String, Object> body = JsonUtil.parseBody(request);
        switch (path) {
            case "/save": {
                AddressService.save(user.getId(),
                        JsonUtil.longValue(body, "id"),
                        JsonUtil.str(body, "receiver"),
                        JsonUtil.str(body, "phone"),
                        JsonUtil.str(body, "detail"),
                        JsonUtil.isTrue(body.get("isDefault")));
                JsonUtil.writeJson(response, Result.ok("保存成功", null));
                break;
            }
            case "/delete": {
                AddressService.delete(user.getId(), JsonUtil.longValue(body, "id"));
                JsonUtil.writeJson(response, Result.ok("已删除", null));
                break;
            }
            case "/set-default": {
                AddressService.setDefault(user.getId(), JsonUtil.longValue(body, "id"));
                JsonUtil.writeJson(response, Result.ok("已设为默认", null));
                break;
            }
            default:
                JsonUtil.writeJson(response, Result.fail("接口不存在"));
        }
    }

}
