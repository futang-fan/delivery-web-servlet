package com.deliveryweb.controller;

import com.deliveryweb.pojo.Shop;
import com.deliveryweb.pojo.User;
import com.deliveryweb.service.ShopService;
import com.deliveryweb.service.StatService;
import com.deliveryweb.util.*;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

/**
 * 店铺接口：/api/shop/apply、/api/shop/audit、/api/shop/list、/api/shop/my。
 * AuthFilter 只做登录态拦截；apply/audit 的角色细分（商家/管理员）在此校验，
 * 因为 /api/shop 前缀下混合了多角色的操作，过滤器无法按前缀区分。
 * 异常兜底与路由工具由 {@link BaseApiServlet} 统一提供。
 */
@WebServlet("/api/shop/*")
public class ShopServlet extends BaseApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected String prefix() {
        return "/api/shop";
    }

    @Override
    protected void dispatch(String path, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        if ("GET".equals(request.getMethod())) {
            switch (path) {
                case "/list":
                    handleList(request, response);
                    break;
                case "/my":
                    handleMy(request, response);
                    break;
                case "/detail":
                    handleDetail(request, response);
                    break;
                case "/stats":
                    handleStats(request, response);
                    break;
                case "/stats/export":
                    handleStatsExport(request, response);
                    break;
                default:
                    JsonUtil.writeJson(response, Result.fail("接口不存在"));
            }
            return;
        }
        switch (path) {
            case "/apply":
                handleApply(request, response);
                break;
            case "/audit":
                handleAudit(request, response);
                break;
            case "/update":
                handleUpdate(request, response);
                break;
            case "/business-status":
                handleBusiness(request, response);
                break;
            default:
                JsonUtil.writeJson(response, Result.fail("接口不存在"));
        }
    }

    /** 入驻申请/整改重提，仅商家角色 */
    private void handleApply(HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        User user = currentUser(request);
        if (!requireRole(user, Constants.ROLE_SHOP, "仅商家可提交入驻申请", response)) {
            return;
        }
        Map<String, Object> body = JsonUtil.parseBody(request);
        ShopService.apply(user.getId(),
                JsonUtil.str(body, "name"),
                JsonUtil.str(body, "contact"),
                JsonUtil.str(body, "phone"),
                JsonUtil.str(body, "address"),
                JsonUtil.str(body, "qualification"));
        JsonUtil.writeJson(response, Result.ok("提交成功，等待管理员审核", null));
    }

    /** 管理员审核（通过/拒绝+原因）。/api/shop 前缀不在过滤器白名单内，必须在此校验 ADMIN */
    private void handleAudit(HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        User user = currentUser(request);
        if (!requireRole(user, Constants.ROLE_ADMIN, "仅管理员可审核店铺", response)) {
            return;
        }
        Map<String, Object> body = JsonUtil.parseBody(request);
        Long shopId = JsonUtil.longValue(body, "shopId");
        ShopService.audit(user.getId(), shopId, JsonUtil.isTrue(body.get("approved")),
                JsonUtil.str(body, "reason"));
        JsonUtil.writeJson(response, Result.ok("审核完成", null));
    }

    /** 顾客端店铺搜索：店名/菜品名关键字 + 营业状态筛选 + 排序方式，分页 */
    private void handleList(HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        Page<Shop> page = Page.of(intParam(request, "page"), intParam(request, "size"));
        ShopService.searchForCustomer(request.getParameter("keyword"),
                request.getParameter("businessStatus"), request.getParameter("sort"), page);
        JsonUtil.writeJson(response, Result.ok(page));
    }

    /** 商家本人的店铺（含审核状态与拒绝原因），P07 页面数据源 */
    private void handleMy(HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        User user = currentUser(request);
        if (!requireRole(user, Constants.ROLE_SHOP, "仅商家可访问本人店铺", response)) {
            return;
        }
        JsonUtil.writeJson(response, Result.ok(ShopService.findByOwner(user.getId())));
    }

    /** 店铺详情：基本资料 + 评分摘要（平均分/评价数/星级分布） */
    private void handleDetail(HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        Long shopId = longParam(request, "shopId");
        Shop shop = shopId == null ? null : ShopService.getDetail(shopId);
        if (shop == null) {
            JsonUtil.writeJson(response, Result.fail("店铺不存在"));
            return;
        }
        // P2-1 修复（2026-09-13）：入驻资质与审核信息仅本店店主本人与管理员可见，
        // 其余登录用户抹掉三字段后再返回，收掉越权信息泄露面（设计书 1.3）
        User viewer = currentUser(request);
        boolean canSeeAudit = viewer != null
                && (Constants.ROLE_ADMIN.equals(viewer.getRole())
                    || viewer.getId().equals(shop.getOwnerId()));
        if (!canSeeAudit) {
            shop.setQualification(null);
            shop.setAuditReason(null);
            shop.setAuditorId(null);
        }
        shop.setRatingSummary(ShopService.ratingSummary(shopId));
        JsonUtil.writeJson(response, Result.ok(shop));
    }

    /** 商家经营数据（仅本人店铺）：订单数、有效交易额、退款金额、完成订单数、热销排行 */
    private void handleStats(HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        User user = currentUser(request);
        if (!requireRole(user, Constants.ROLE_SHOP, "仅商家可查看经营数据", response)) {
            return;
        }
        Shop shop = ShopService.findByOwner(user.getId());
        if (shop == null) {
            JsonUtil.writeJson(response, Result.fail("未找到店铺记录"));
            return;
        }
        JsonUtil.writeJson(response, Result.ok(StatService.merchantStats(
                shop.getId(),
                request.getParameter("startDate"),
                request.getParameter("endDate"))));
    }

    /** 经营数据 CSV 导出（仅本人店铺）：绕过 JSON 统一响应直写 text/csv；
     *  开头写 UTF-8 BOM，否则中文用 Excel 打开是乱码 */
    private void handleStatsExport(HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        User user = currentUser(request);
        if (!requireRole(user, Constants.ROLE_SHOP, "仅商家可导出经营数据", response)) {
            return;
        }
        Shop shop = ShopService.findByOwner(user.getId());
        if (shop == null) {
            JsonUtil.writeJson(response, Result.fail("未找到店铺记录"));
            return;
        }
        Map<String, Object> stats = StatService.merchantStats(
                shop.getId(),
                request.getParameter("startDate"),
                request.getParameter("endDate"));
        CsvUtil.begin(response, "shop-stats.csv");
        PrintWriter out = response.getWriter();
        out.print(CsvUtil.row("指标", "数值"));
        out.print(CsvUtil.row("订单数", stats.get("orderCount")));
        out.print(CsvUtil.row("有效交易额", stats.get("validAmount")));
        out.print(CsvUtil.row("退款金额", stats.get("refundAmount")));
        out.print(CsvUtil.row("完成订单数", stats.get("completedCount")));
        out.print(CsvUtil.row());
        out.print(CsvUtil.row("热销菜品", "销量(件)"));
        Object rank = stats.get("hotDishRank");
        if (rank instanceof List) {
            for (Object row : (List<?>) rank) {
                if (row instanceof Map) {
                    Map<?, ?> m = (Map<?, ?>) row;
                    out.print(CsvUtil.row(m.get("name"), m.get("sales")));
                }
            }
        }
        out.flush();
    }

    /** 店铺资料维护：名称/简介/联系电话/地址，仅审核通过商家（归属校验在 Service） */
    private void handleUpdate(HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        User user = currentUser(request);
        if (!requireRole(user, Constants.ROLE_SHOP, "仅商家可维护店铺资料", response)) {
            return;
        }
        Map<String, Object> body = JsonUtil.parseBody(request);
        ShopService.updateMyShop(user.getId(),
                JsonUtil.str(body, "name"),
                JsonUtil.str(body, "description"),
                JsonUtil.str(body, "phone"),
                JsonUtil.str(body, "address"));
        JsonUtil.writeJson(response, Result.ok("保存成功", null));
    }

    /** 营业/休息切换，仅审核通过商家 */
    private void handleBusiness(HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        User user = currentUser(request);
        if (!requireRole(user, Constants.ROLE_SHOP, "仅商家可切换营业状态", response)) {
            return;
        }
        Map<String, Object> body = JsonUtil.parseBody(request);
        ShopService.changeBusinessStatus(user.getId(), JsonUtil.str(body, "businessStatus"));
        JsonUtil.writeJson(response, Result.ok("已切换营业状态", null));
    }

}
