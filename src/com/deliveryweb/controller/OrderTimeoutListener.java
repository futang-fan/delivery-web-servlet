package com.deliveryweb.controller;

import com.deliveryweb.service.OrderService;
import com.deliveryweb.util.Constants;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 未支付订单超时取消监听器。
 * 容器启动时启动守护定时器，每 {@link Constants#TIMEOUT_SCAN_SECONDS} 秒扫描
 * 超过 {@link Constants#ORDER_TIMEOUT_MINUTES} 分钟未支付的订单并自动取消、释放库存；
 * 与订单列表的惰性检查共用 {@link OrderService#cancelExpiredOrders()}。
 */
@WebListener
public class OrderTimeoutListener implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(OrderTimeoutListener.class.getName());

    /** 单线程守护调度器：容器销毁时关闭 */
    private ScheduledExecutorService executor;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "order-timeout-scanner");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleAtFixedRate(() -> {
            try {
                int cancelled = OrderService.cancelExpiredOrders();
                if (cancelled > 0) {
                    LOG.info("超时取消未支付订单 " + cancelled + " 笔");
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "超时取消扫描异常", e);
            }
        }, Constants.TIMEOUT_SCAN_SECONDS, Constants.TIMEOUT_SCAN_SECONDS, TimeUnit.SECONDS);
        executor.scheduleAtFixedRate(() -> {
            try {
                int completed = OrderService.completeExpiredDelivered();
                if (completed > 0) {
                    LOG.info("超时自动完成已送达订单 " + completed + " 笔");
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "已送达自动完成扫描异常", e);
            }
        }, Constants.DELIVERED_SCAN_SECONDS, Constants.DELIVERED_SCAN_SECONDS, TimeUnit.SECONDS);
        LOG.info("未支付订单超时取消定时器已启动：阈值 " + Constants.ORDER_TIMEOUT_MINUTES
                + " 分钟，周期 " + Constants.TIMEOUT_SCAN_SECONDS + " 秒；已送达自动完成阈值 "
                + Constants.DELIVERED_AUTO_COMPLETE_HOURS + " 小时，周期 " + Constants.DELIVERED_SCAN_SECONDS + " 秒");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
