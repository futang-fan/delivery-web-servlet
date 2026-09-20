package com.deliveryweb.service;

import com.deliveryweb.dao.StatDao;
import com.deliveryweb.util.Constants;
import com.deliveryweb.util.MyBatisUtil;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 平台统计业务逻辑。
 * 订单数量与交易金额取已支付且未退款口径；排行各取前 5。
 *
 * @author DeliveryWeb
 */
public final class StatService {

    /** 排行榜单长度 */
    private static final int RANK_LIMIT = 5;

    private StatService() {
    }

    /** 平台统计概览：{orderCount, totalAmount}；日期可选，空值即建库以来全量 */
    public static Map<String, Object> overview(String startDate, String endDate) {
        return MyBatisUtil.withMapper(StatDao.class, dao -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("orderCount", dao.countPaidOrders(Constants.PAY_PAID, startDate, endDate));
            data.put("totalAmount", dao.sumPaidAmount(Constants.PAY_PAID, startDate, endDate));
            return data;
        });
    }

    /** 销量排行四口径一次返回：{shopAmountRank, shopQuantityRank, dishQuantityRank, dishAmountRank}，前端选择口径后切换展示；日期可选 */
    public static Map<String, Object> ranking(String startDate, String endDate) {
        return MyBatisUtil.withMapper(StatDao.class, dao -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("shopAmountRank", dao.shopRank(Constants.PAY_PAID, startDate, endDate, RANK_LIMIT));
            data.put("shopQuantityRank", dao.shopQuantityRank(Constants.PAY_PAID, startDate, endDate, RANK_LIMIT));
            data.put("dishQuantityRank", dao.dishRank(Constants.PAY_PAID, startDate, endDate, RANK_LIMIT));
            data.put("dishAmountRank", dao.dishAmountRank(Constants.PAY_PAID, startDate, endDate, RANK_LIMIT));
            return data;
        });
    }

    /** 商家经营数据（仅本人店铺）：订单数、有效交易额、退款金额、完成订单数、热销菜品排行 */
    public static Map<String, Object> merchantStats(Long shopId, String startDate, String endDate) {
        return MyBatisUtil.withMapper(StatDao.class, dao -> {
            Map<String, Object> data = new LinkedHashMap<>(
                    dao.merchantOverview(shopId, startDate, endDate));
            data.put("hotDishRank", dao.merchantDishRank(shopId, Constants.PAY_PAID, startDate, endDate, RANK_LIMIT));
            return data;
        });
    }
}
