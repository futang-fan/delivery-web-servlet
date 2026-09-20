package com.deliveryweb.controller;

import com.deliveryweb.pojo.Order;
import com.deliveryweb.pojo.Shop;
import com.deliveryweb.pojo.User;
import com.deliveryweb.service.*;
import com.deliveryweb.util.*;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员接口：/api/admin/**
 * shops 店铺列表（D3）；users/user-status/create-rider/shop-status 用户管理与店铺监管；
 * orders 订单监管与 stats 统计（D9）；refunds/refund-decide 退款仲裁；
 * reset-requests/reset-decide 找回审批、管理员协助重置密码。
 * 角色限制由 AuthFilter 的 /api/admin/ 白名单统一保证，Servlet 内无需重复校验。
 * 异常兜底与路由工具由 {@link BaseApiServlet} 统一提供。
 */
@WebServlet("/api/admin/*")
public class AdminServlet extends BaseApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected String prefix() {
        return "/api/admin";
    }

    @Override
    protected void dispatch(String path, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        if ("GET".equals(request.getMethod())) {
            if ("/shops".equals(path)) {
                Page<Shop> page = Page.of(intParam(request, "page"), intParam(request, "size"));
                ShopService.searchForAdmin(request.getParameter("auditStatus"),
                        request.getParameter("businessStatus"), page);
                JsonUtil.writeJson(response, Result.ok(page));
                return;
            }
            if ("/users".equals(path)) {
                Page<User> page = UserService.listForAdmin(request.getParameter("role"),
                        request.getParameter("status"), request.getParameter("keyword"),
                        Page.of(intParam(request, "page"), intParam(request, "size")));
                JsonUtil.writeJson(response, Result.ok(page));
                return;
            }
            if ("/orders".equals(path)) {
                Page<com.deliveryweb.pojo.Order> page = OrderService.listForAdmin(
                        request.getParameter("orderNo"), request.getParameter("username"),
                        request.getParameter("shopName"), request.getParameter("startDate"),
                        request.getParameter("endDate"), request.getParameter("orderStatus"),
                        Page.of(intParam(request, "page"), intParam(request, "size")));
                JsonUtil.writeJson(response, Result.ok(page));
                return;
            }
            if ("/orders/export".equals(path)) {
                List<Order> rows = OrderService.listForAdminExport(
                        request.getParameter("orderNo"), request.getParameter("username"),
                        request.getParameter("shopName"), request.getParameter("startDate"),
                        request.getParameter("endDate"), request.getParameter("orderStatus"));
                // P3-2 修复（2026-09-13）：截断标记头必须在 CsvUtil.begin 之前写（begin 内部 getWriter 并写 BOM）；
                // 边界修正（2026-09-13 审查 P1-1）：DAO 多取 1 行作探测，“实际总数 > 上限”才是真截断——
                // 恰好等于上限时数据完整，不得误报；输出仅取前 limit 行，探测行不入 CSV
                int limit = OrderService.ADMIN_EXPORT_LIMIT;
                boolean truncated = rows.size() > limit;
                int exportRows = Math.min(rows.size(), limit);
                response.setHeader("X-Export-Truncated", truncated ? "1" : "0");
                response.setHeader("X-Export-Rows", String.valueOf(exportRows));
                CsvUtil.begin(response, "admin-orders.csv");
                PrintWriter out = response.getWriter();
                out.print(CsvUtil.row("订单号", "顾客", "店铺", "实付金额", "支付状态", "订单状态", "下单时间"));
                for (int i = 0; i < exportRows; i++) {
                    Order o = rows.get(i);
                    out.print(CsvUtil.row(o.getOrderNo(), o.getCustomerName(), o.getShopName(),
                            o.getPayAmount(), o.getPayStatus(), o.getOrderStatus(), o.getCreatedAt()));
                }
                out.flush();
                return;
            }
            if ("/stats/overview".equals(path)) {
                JsonUtil.writeJson(response, Result.ok(StatService.overview(
                        request.getParameter("startDate"), request.getParameter("endDate"))));
                return;
            }
            if ("/stats/ranking".equals(path)) {
                JsonUtil.writeJson(response, Result.ok(StatService.ranking(
                        request.getParameter("startDate"), request.getParameter("endDate"))));
                return;
            }
            if ("/refunds".equals(path)) {
                Page<com.deliveryweb.pojo.RefundVO> page = RefundService.pendingForAdmin(
                        Page.of(intParam(request, "page"), intParam(request, "size")));
                JsonUtil.writeJson(response, Result.ok(page));
                return;
            }
            if ("/reset-requests".equals(path)) {
                // 找回审批：内存中的找回申请列表（ResetCodeStore，无数据库表）
                JsonUtil.writeJson(response, Result.ok(ResetCodeStore.list()));
                return;
            }
            JsonUtil.writeJson(response, Result.fail("接口不存在"));
            return;
        }
        User admin = currentUser(request);
        Map<String, Object> body = JsonUtil.parseBody(request);
        switch (path) {
            case "/user-status": {
                UserService.changeStatus(admin.getId(), JsonUtil.longValue(body, "userId"),
                        JsonUtil.str(body, "status"));
                JsonUtil.writeJson(response, Result.ok("账号状态已更新", null));
                break;
            }
            case "/create-rider": {
                Long riderId = UserService.createRider(JsonUtil.str(body, "username"),
                        JsonUtil.str(body, "password"), JsonUtil.str(body, "phone"));
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("id", riderId);
                JsonUtil.writeJson(response, Result.ok("骑手账号已创建", data));
                break;
            }
            case "/refund-decide": {
                // 审查 P1-3（2026-09-12）：缺省按同意属 fail-open（退款不可逆），统一走 JsonUtil.isTrue（缺省驳回）
                boolean agree = JsonUtil.isTrue(body.get("agree"));
                RefundService.adminHandle(JsonUtil.longValue(body, "refundId"),
                        agree, JsonUtil.str(body, "adminRemark"));
                JsonUtil.writeJson(response, Result.ok(agree ? "平台已裁定同意退款" : "平台已裁定驳回退款", null));
                break;
            }
            case "/reset-decide": {
                // 找回审批：同意生成一次性验证码（管理端展示后线下告知申请人）；拒绝作废申请
                Long requestId = JsonUtil.longValue(body, "requestId");
                boolean agree = JsonUtil.isTrue(body.get("agree"));
                String code = ResetCodeStore.decide(requestId == null ? -1L : requestId, agree);
                Map<String, Object> data = new LinkedHashMap<>();
                if (code != null) {
                    data.put("code", code);
                }
                JsonUtil.writeJson(response,
                        Result.ok(agree ? "已同意，请将验证码线下告知申请人" : "申请已作废", data));
                break;
            }
            case "/shop-status": {
                if (!Constants.BUSINESS_CLOSED.equals(JsonUtil.str(body, "businessStatus"))) {
                    JsonUtil.writeJson(response, Result.fail("监管操作仅支持强制休息"));
                    return;
                }
                ShopService.forceClose(JsonUtil.longValue(body, "shopId"));
                JsonUtil.writeJson(response, Result.ok("已强制暂停该店铺营业", null));
                break;
            }
            default:
                JsonUtil.writeJson(response, Result.fail("接口不存在"));
        }
    }
}
