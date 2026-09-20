package com.deliveryweb.service;

import com.deliveryweb.dao.DishDao;
import com.deliveryweb.pojo.Dish;
import com.deliveryweb.pojo.Shop;
import com.deliveryweb.util.BizException;
import com.deliveryweb.util.Constants;
import com.deliveryweb.util.MyBatisUtil;
import com.deliveryweb.util.Page;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 菜品业务逻辑：新增/修改（同店名唯一）、上下架（软删除）、
 * 本店列表与实际售卖列表。库存的【行级条件扣减】在下单时使用，本类只维护数量。
 * 所有操作先校验商家与店铺归属、店铺审核通过状态。
 */
public final class DishService {

    private DishService() {
    }

    /**
     * 新增或修改菜品：新增默认下架，修改做归属校验。
     */
    public static void save(Long sellerId, Long dishId, String name, BigDecimal price,
                            Integer stock, String imageUrl, String description) {
        if (name == null || name.length() < 1 || name.length() > 50) {
            throw new BizException("菜品名称须为 1-50 个字符");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0 || price.scale() > 2) {
            throw new BizException("价格须为正数且最多两位小数");
        }
        if (stock == null || stock < 0) {
            throw new BizException("库存须为非负整数");
        }
        if (description != null && description.length() > 200) {
            throw new BizException("菜品描述不能超过 200 字");
        }

        Long shopId = resolveShopId(sellerId);
        Dish exist = MyBatisUtil.withMapper(DishDao.class,
                dao -> dao.findByShopAndName(shopId, name));
        if (exist != null && !exist.getId().equals(dishId)) {
            throw new BizException("该店铺下已存在同名菜品");
        }

        Dish dish = new Dish();
        dish.setId(dishId);
        dish.setShopId(shopId);
        dish.setName(name);
        dish.setDescription(description);
        dish.setPrice(price);
        dish.setStock(stock);
        dish.setImageUrl(imageUrl);

        MyBatisUtil.withTx(session -> {
            DishDao dao = session.getMapper(DishDao.class);
            if (dishId == null) {
                dish.setStatus(Constants.DISH_STATUS_OFF_SALE);
                dish.setCreatedAt(LocalDateTime.now());
                dao.insert(dish);
            } else if (dao.update(dish) == 0) {
                throw new BizException("菜品不存在或无权限");
            }
            return null;
        });
    }

    /** 上下架：下架即软删除，可重新上架（保留数据）。归属校验后在 UPDATE 中双重兜底。 */
    public static void changeStatus(Long sellerId, Long dishId, String status) {
        if (!Constants.DISH_STATUS_ON_SALE.equals(status)
                && !Constants.DISH_STATUS_OFF_SALE.equals(status)) {
            throw new BizException("上下架状态值不合法");
        }
        Long shopId = resolveShopId(sellerId);
        int rows = MyBatisUtil.withTx(session -> session.getMapper(DishDao.class)
                .updateStatus(dishId, shopId, status));
        if (rows == 0) {
            throw new BizException("菜品不存在或无权限");
        }
    }

    /** 商家本店全部菜品（含下架）*/
    public static List<Dish> myDishes(Long sellerId) {
        Long shopId = resolveShopId(sellerId);
        return MyBatisUtil.withMapper(DishDao.class,
                dao -> dao.listByShop(shopId, null));
    }

    /** 店铺在售菜品（仅上架）*/
    public static List<Dish> listOnSale(Long shopId) {
        return MyBatisUtil.withMapper(DishDao.class,
                dao -> dao.listByShop(shopId, Constants.DISH_STATUS_ON_SALE));
    }

    /** 顾客全局菜品搜索（跨店、仅在售、审核通过店铺），联表返回店铺名与营业状态 */
    public static Page<Dish> search(String keyword, Long shopId, BigDecimal minPrice, BigDecimal maxPrice,
                                    Page<Dish> page) {
        page.setTotal(MyBatisUtil.withMapper(DishDao.class,
                dao -> dao.countSearch(keyword, shopId, minPrice, maxPrice)));
        if (page.getTotal() > 0) {
            page.setRecords(MyBatisUtil.withMapper(DishDao.class,
                    dao -> dao.searchDish(keyword, shopId, minPrice, maxPrice, page.offset(), page.getSize())));
        }
        return page;
    }

    /** 商家操作前的公共校验：有店铺且审核通过后才可管理菜品 */
    private static Long resolveShopId(Long sellerId) {
        Shop shop = ShopService.findByOwner(sellerId);
        if (shop == null) {
            throw new BizException("未找到店铺记录，请先完成入驻");
        }
        if (!Constants.SHOP_AUDIT_APPROVED.equals(shop.getAuditStatus())) {
            throw new BizException("店铺未通过审核，暂不能管理菜品");
        }
        return shop.getId();
    }
}
