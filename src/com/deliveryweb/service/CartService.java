package com.deliveryweb.service;

import com.deliveryweb.dao.DishDao;
import com.deliveryweb.pojo.CartItem;
import com.deliveryweb.pojo.Dish;
import com.deliveryweb.pojo.Shop;
import com.deliveryweb.util.BizException;
import com.deliveryweb.util.Constants;
import com.deliveryweb.util.MyBatisUtil;

import javax.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 购物车业务逻辑：会话级临时数据，不落库。
 * 规则：同店约束（跨店提示先清空/切换）、仅上架且库存≥数量可加入、
 * 数量最小为 1、金额由服务端按当前价格实时计算（提交订单时还会重新校验）。
 * 购物车以 map（菜品号 -&gt; CartItem）形式存于 Session。
 */
public final class CartService {

    private CartService() {
    }

    /** 当前购物车摘要：shopId、shopName、items（含小计）、totalAmount、isEmpty */
    public static Map<String, Object> list(HttpSession session) {
        Map<Long, CartItem> cart = getCart(session);
        List<CartItem> items = new ArrayList<>(cart.values());
        BigDecimal total = BigDecimal.ZERO;
        for (CartItem item : items) {
            BigDecimal subtotal = item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            item.setSubtotal(subtotal);
            total = total.add(subtotal);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("isEmpty", items.isEmpty());
        result.put("shopId", items.isEmpty() ? null : items.get(0).getShopId());
        result.put("shopName", items.isEmpty() ? null : shopName(items.get(0).getShopId()));
        result.put("totalAmount", total);
        result.put("items", items);
        return result;
    }

    /** 加购：校验上下架与库存、同店约束，同一菜品累加数量 */
    public static void add(HttpSession session, Long dishId, Integer quantity) {
        if (dishId == null || quantity == null || quantity < 1) {
            throw new BizException("请选择菜品和数量");
        }
        Dish dish = MyBatisUtil.withMapper(DishDao.class, dao -> dao.findById(dishId));
        if (dish == null) {
            throw new BizException("菜品不存在");
        }
        if (!Constants.DISH_STATUS_ON_SALE.equals(dish.getStatus())) {
            throw new BizException("该菜品已下架");
        }
        if (dish.getStock() < quantity) {
            throw new BizException("库存不足");
        }

        Map<Long, CartItem> cart = getCart(session);
        if (!cart.isEmpty()) {
            CartItem any = cart.values().iterator().next();
            if (!any.getShopId().equals(dish.getShopId())) {
                throw new BizException("购物车已有其他店铺商品，请先清空或切换");
            }
        }

        CartItem item = cart.get(dishId);
        int targetQuantity = item == null ? quantity : item.getQuantity() + quantity;
        if (targetQuantity > dish.getStock()) {
            throw new BizException("库存不足");
        }
        if (item == null) {
            item = new CartItem();
            item.setDishId(dish.getId());
            item.setShopId(dish.getShopId());
            item.setDishName(dish.getName());
            item.setPrice(dish.getPrice());
            item.setQuantity(quantity);
            cart.put(dishId, item);
        } else {
            item.setQuantity(targetQuantity);
        }
        session.setAttribute(Constants.SESSION_CART, cart);
    }

    /** 修改数量：最小为 1（删除用 remove），校验菜品仍上架且不超库存 */
    public static void updateCount(HttpSession session, Long dishId, Integer quantity) {
        Map<Long, CartItem> cart = getCart(session);
        CartItem item = cart.get(dishId);
        if (item == null) {
            throw new BizException("购物车中没有该菜品");
        }
        if (quantity == null || quantity < 1) {
            throw new BizException("数量至少为 1");
        }
        Dish dish = MyBatisUtil.withMapper(DishDao.class, dao -> dao.findById(dishId));
        if (dish == null) {
            throw new BizException("菜品不存在");
        }
        // 加购后菜品被下架时禁止修改数量（P1 收口）；下单时仍有二次校验兜底
        if (!Constants.DISH_STATUS_ON_SALE.equals(dish.getStatus())) {
            throw new BizException("该菜品已下架");
        }
        if (dish.getStock() < quantity) {
            throw new BizException("库存不足");
        }
        item.setQuantity(quantity);
        session.setAttribute(Constants.SESSION_CART, cart);
    }

    public static void remove(HttpSession session, Long dishId) {
        Map<Long, CartItem> cart = getCart(session);
        cart.remove(dishId);
        if (cart.isEmpty()) {
            session.removeAttribute(Constants.SESSION_CART);
        } else {
            session.setAttribute(Constants.SESSION_CART, cart);
        }
    }

    public static void clear(HttpSession session) {
        session.removeAttribute(Constants.SESSION_CART);
    }

    private static Map<Long, CartItem> getCart(HttpSession session) {
        Object cart = session.getAttribute(Constants.SESSION_CART);
        return cart == null ? new LinkedHashMap<>() : (Map<Long, CartItem>) cart;
    }

    private static String shopName(Long shopId) {
        Shop shop = ShopService.getDetail(shopId);
        return shop == null ? null : shop.getName();
    }
}
