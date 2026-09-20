package com.deliveryweb.service;

import com.deliveryweb.dao.ShopDao;
import com.deliveryweb.pojo.Shop;
import com.deliveryweb.pojo.ShopRatingSummary;
import com.deliveryweb.util.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 店铺业务逻辑：入驻申请、管理员审核（拒绝必填原因、防重复审核）、
 * 店铺分页搜索与本人店铺查询。
 */
public final class ShopService {

    private ShopService() {
    }

    /**
     * 入驻申请/整改重提：校验五项资料后覆盖保存，审核状态回到待审核。
     * 注册商家时已自动生成占位记录（UserService），审核通过后不允许重复申请。
     */
    public static void apply(Long ownerId, String name, String contact, String phone,
                             String address, String qualification) {
        if (name == null || name.length() < 2 || name.length() > 50) {
            throw new BizException("店铺名称须为 2-50 个字符");
        }
        if (contact == null || contact.isEmpty()) {
            throw new BizException("请填写联系人姓名");
        }
        checkMax(contact, 50, "联系人姓名不能超过 50 个字符");
        if (phone == null || !Constants.PHONE_PATTERN.matcher(phone).matches()) {
            throw new BizException("联系电话须为 11 位数字");
        }
        if (address == null || address.isEmpty()) {
            throw new BizException("请填写经营地址");
        }
        checkMax(address, 200, "经营地址不能超过 200 个字符");
        if (qualification == null || qualification.isEmpty()) {
            throw new BizException("请填写资质说明");
        }
        checkMax(qualification, 200, "资质说明不能超过 200 个字符");

        Shop shop = MyBatisUtil.withMapper(ShopDao.class, dao -> dao.findByOwnerId(ownerId));
        if (shop == null) {
            throw new BizException("未找到店铺记录，请重新登录");
        }
        if (Constants.SHOP_AUDIT_APPROVED.equals(shop.getAuditStatus())) {
            throw new BizException("店铺已审核通过，无需重复提交入驻申请");
        }

        shop.setName(name);
        shop.setContact(contact);
        shop.setPhone(phone);
        shop.setAddress(address);
        shop.setQualification(qualification);
        shop.setAuditStatus(Constants.SHOP_AUDIT_PENDING);
        MyBatisUtil.withTx(session -> session.getMapper(ShopDao.class).updateApplyInfo(shop));
    }

    /**
     * 管理员审核：仅待审核店铺可审（防重复审核）；拒绝时原因必填，通过时原因可留空。
     * 记录审核时间与操作人，供商家查看与平台追溯。
     */
    public static void audit(Long auditorId, Long shopId, boolean approved, String reason) {
        Shop shop = MyBatisUtil.withMapper(ShopDao.class, dao -> dao.findById(shopId));
        if (shop == null) {
            throw new BizException("店铺不存在");
        }
        if (!Constants.SHOP_AUDIT_PENDING.equals(shop.getAuditStatus())) {
            throw new BizException("该店铺已审核过，不能重复审核");
        }
        if (!approved && (reason == null || reason.isEmpty())) {
            throw new BizException("拒绝审核时必须填写原因");
        }
        // P3-4 修复（2026-09-13）：审核意见补长度校验（列宽 VARCHAR(500)，口径对齐 init.sql）
        checkMax(reason, 500, "审核原因不能超过 500 个字符");

        shop.setAuditStatus(approved ? Constants.SHOP_AUDIT_APPROVED : Constants.SHOP_AUDIT_REJECTED);
        shop.setAuditReason(JsonUtil.trimToNull(reason));
        shop.setAuditedAt(LocalDateTime.now());
        shop.setAuditorId(auditorId);
        MyBatisUtil.withTx(session -> session.getMapper(ShopDao.class).updateAudit(shop));
    }

    /**
     * 顾客端店铺搜索：仅审核通过店铺，营业中的店铺排在前面；
     * sort 支持 DEFAULT（默认）/RATING（评分优先）/SALES（销量优先），营业中排前不受排序影响。
     */
    public static Page<Shop> searchForCustomer(String keyword, String businessStatus, String sort, Page<Shop> page) {
        String key = JsonUtil.trimToNull(keyword);
        String status = normalizeBusinessStatus(businessStatus);
        String order = normalizeSort(sort);
        List<Shop> records = MyBatisUtil.withMapper(ShopDao.class,
                dao -> dao.searchForCustomer(Constants.SHOP_AUDIT_APPROVED, key, status, order,
                        page.offset(), page.getSize()));
        long total = MyBatisUtil.withMapper(ShopDao.class,
                dao -> dao.countForCustomer(Constants.SHOP_AUDIT_APPROVED, key, status));
        page.setTotal(total);
        page.setRecords(records);
        return page;
    }

    /** 排序方式白名单：空值归 DEFAULT，非法值抛业务异常（避免拼进 SQL 的排序键失控） */
    private static String normalizeSort(String sort) {
        String s = JsonUtil.trimToNull(sort);
        if (s == null || "DEFAULT".equals(s)) {
            return "DEFAULT";
        }
        if ("RATING".equals(s) || "SALES".equals(s)) {
            return s;
        }
        throw new BizException("排序方式不合法");
    }

    /**
     * 管理员店铺列表：按审核状态/营业状态可选筛选，待审核排前。
     */
    public static Page<Shop> searchForAdmin(String auditStatus, String businessStatus, Page<Shop> page) {
        String audit = JsonUtil.trimToNull(auditStatus);
        if (audit != null && !Constants.SHOP_AUDIT_PENDING.equals(audit)
                && !Constants.SHOP_AUDIT_APPROVED.equals(audit)
                && !Constants.SHOP_AUDIT_REJECTED.equals(audit)) {
            throw new BizException("审核状态筛选值不合法");
        }
        String status = normalizeBusinessStatus(businessStatus);
        List<Shop> records = MyBatisUtil.withMapper(ShopDao.class,
                dao -> dao.searchForAdmin(audit, status, page.offset(), page.getSize()));
        long total = MyBatisUtil.withMapper(ShopDao.class,
                dao -> dao.countForAdmin(audit, status));
        page.setTotal(total);
        page.setRecords(records);
        return page;
    }

    /**
     * 查询商家本人的店铺；注册后未生成记录时返回 null。
     */
    public static Shop findByOwner(Long ownerId) {
        return MyBatisUtil.withMapper(ShopDao.class, dao -> dao.findByOwnerId(ownerId));
    }

    /** 审核通过的商家可维护店铺资料：名称/简介/电话/地址（归属校验） */
    public static void updateMyShop(Long ownerId, String name, String description,
                                    String phone, String address) {
        if (name == null || name.length() < 2 || name.length() > 50) {
            throw new BizException("店铺名称须为 2-50 个字符");
        }
        if (phone == null || !Constants.PHONE_PATTERN.matcher(phone).matches()) {
            throw new BizException("联系电话须为 11 位数字");
        }
        if (address == null || address.isEmpty()) {
            throw new BizException("请填写经营地址");
        }
        checkMax(address, 200, "经营地址不能超过 200 个字符");
        checkMax(description, 500, "店铺简介不能超过 500 个字符");
        Shop shop = findByOwner(ownerId);
        if (shop == null) {
            throw new BizException("未找到店铺记录，请重新登录");
        }
        if (!Constants.SHOP_AUDIT_APPROVED.equals(shop.getAuditStatus())) {
            throw new BizException("店铺未通过审核，暂不能修改资料");
        }
        shop.setName(name);
        shop.setDescription(JsonUtil.trimToNull(description));
        shop.setPhone(phone);
        shop.setAddress(address);
        MyBatisUtil.withTx(session -> session.getMapper(ShopDao.class).updateInfo(shop));
    }

    /** 营业/休息切换：仅审核通过店铺；未审核店铺不能营业 */
    public static void changeBusinessStatus(Long ownerId, String businessStatus) {
        if (!Constants.BUSINESS_OPEN.equals(businessStatus)
                && !Constants.BUSINESS_CLOSED.equals(businessStatus)) {
            throw new BizException("营业状态值不合法");
        }
        Shop shop = findByOwner(ownerId);
        if (shop == null) {
            throw new BizException("未找到店铺记录，请重新登录");
        }
        if (!Constants.SHOP_AUDIT_APPROVED.equals(shop.getAuditStatus())) {
            throw new BizException("店铺未通过审核，不能切换营业状态");
        }
        shop.setBusinessStatus(businessStatus);
        MyBatisUtil.withTx(session -> session.getMapper(ShopDao.class).updateBusinessStatus(shop));
    }

    /** 店铺详情（P03 头部）：返回基本资料 */
    public static Shop getDetail(Long shopId) {
        return MyBatisUtil.withMapper(ShopDao.class, dao -> dao.findById(shopId));
    }

    /** 店铺评分摘要（P03 展示）：无评价时返回全 0 摘要（平均 0），不返回 null/空对象 */
    public static ShopRatingSummary ratingSummary(Long shopId) {
        ShopRatingSummary summary = MyBatisUtil.withMapper(ShopDao.class,
                dao -> dao.ratingSummary(shopId));
        if (summary == null) {
            summary = new ShopRatingSummary();
            summary.setAverageRating(BigDecimal.ZERO);
        }
        if (summary.getAverageRating() != null) {
            summary.setAverageRating(summary.getAverageRating().setScale(1, RoundingMode.HALF_UP));
        }
        return summary;
    }

    /** 管理员强制暂停营业：仅营业中店铺可被强制关闭 */
    public static void forceClose(Long shopId) {
        if (shopId == null) {
            throw new BizException("缺少店铺参数");
        }
        Shop shop = MyBatisUtil.withMapper(ShopDao.class, dao -> dao.findById(shopId));
        if (shop == null) {
            throw new BizException("店铺不存在");
        }
        if (!Constants.BUSINESS_OPEN.equals(shop.getBusinessStatus())) {
            throw new BizException("该店铺已处于休息状态");
        }
        int rows = MyBatisUtil.withTx(tx -> tx.getMapper(ShopDao.class)
                .updateBusinessStatusById(shopId, Constants.BUSINESS_OPEN, Constants.BUSINESS_CLOSED));
        if (rows == 0) {
            throw new BizException("店铺状态已变化，请刷新重试");
        }
    }

    /** 长度上限校验（口径对齐 init.sql 列宽）：超长抛业务异常，避免严格模式错误暴露为“系统繁忙” */
    private static void checkMax(String value, int max, String message) {
        if (value != null && value.length() > max) {
            throw new BizException(message);
        }
    }

    private static String normalizeBusinessStatus(String businessStatus) {
        String status = JsonUtil.trimToNull(businessStatus);
        if (status != null && !Constants.BUSINESS_OPEN.equals(status)
                && !Constants.BUSINESS_CLOSED.equals(status)) {
            throw new BizException("营业状态筛选值不合法");
        }
        return status;
    }

}
