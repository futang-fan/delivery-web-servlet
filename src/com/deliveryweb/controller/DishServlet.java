package com.deliveryweb.controller;

import com.deliveryweb.pojo.Dish;
import com.deliveryweb.pojo.User;
import com.deliveryweb.service.DishService;
import com.deliveryweb.util.*;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 菜品接口：/api/dish/save、/status、/my（商家）、/list（顾客按店铺浏览）。
 * /api/dish 前缀混有商家与顾客两类操作：写操作做 SHOP 角色校验，
 * 顾客浏览仅需登录（不按角色限制）。
 * 异常兜底与路由工具由 {@link BaseApiServlet} 统一提供。
 */
@WebServlet("/api/dish/*")
public class DishServlet extends BaseApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected String prefix() {
        return "/api/dish";
    }

    @Override
    protected void dispatch(String path, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        if ("GET".equals(request.getMethod())) {
            switch (path) {
                case "/my":
                    if (requireRole(currentUser(request), Constants.ROLE_SHOP, "仅商家可管理菜品", response)) {
                        JsonUtil.writeJson(response, Result.ok(
                                DishService.myDishes(currentUser(request).getId())));
                    }
                    break;
                case "/list": {
                    Long shopId = longParam(request, "shopId");
                    if (shopId == null) {
                        JsonUtil.writeJson(response, Result.fail("缺少店铺参数"));
                        return;
                    }
                    JsonUtil.writeJson(response, Result.ok(DishService.listOnSale(shopId)));
                    break;
                }
                case "/search": {
                    String keyword = request.getParameter("keyword");
                    keyword = keyword == null ? null : keyword.trim();
                    Page<Dish> page = Page.of(intParam(request, "page"), intParam(request, "size"));
                    JsonUtil.writeJson(response, Result.ok(DishService.search(
                            keyword,
                            longParam(request, "shopId"),
                            toBigDecimal(request.getParameter("minPrice")),
                            toBigDecimal(request.getParameter("maxPrice")),
                            page)));
                    break;
                }
                default:
                    JsonUtil.writeJson(response, Result.fail("接口不存在"));
            }
            return;
        }
        if (!requireRole(currentUser(request), Constants.ROLE_SHOP, "仅商家可管理菜品", response)) {
            return;
        }
        User user = currentUser(request);
        Map<String, Object> body = JsonUtil.parseBody(request);
        switch (path) {
            case "/save": {
                DishService.save(user.getId(),
                        JsonUtil.longValue(body, "id"),
                        JsonUtil.str(body, "name"),
                        toBigDecimal(body.get("price")),
                        stockOf(body),
                        JsonUtil.str(body, "imageUrl"),
                        JsonUtil.str(body, "description"));
                JsonUtil.writeJson(response, Result.ok("保存成功", null));
                break;
            }
            case "/status": {
                DishService.changeStatus(user.getId(),
                        JsonUtil.longValue(body, "id"),
                        JsonUtil.str(body, "status"));
                JsonUtil.writeJson(response, Result.ok("已更新状态", null));
                break;
            }
            default:
                JsonUtil.writeJson(response, Result.fail("接口不存在"));
        }
    }

    /** JSON 数字字段转 BigDecimal，兼容整数/浮点/字符串 */
    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 库存字段：必须为非负整数，拒绝小数/非数字（不允许隐式取整） */
    private Integer stockOf(Map<String, Object> body) {
        Object value = body.get("stock");
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            double d = ((Number) value).doubleValue();
            if (d != Math.floor(d)) {
                throw new BizException("库存须为非负整数");
            }
            return ((Number) value).intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new BizException("库存须为非负整数");
        }
    }
}
