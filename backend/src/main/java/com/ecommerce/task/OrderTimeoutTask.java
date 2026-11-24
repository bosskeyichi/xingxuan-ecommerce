package com.ecommerce.task;

import com.ecommerce.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 订单超时自动取消任务：释放被长时间占用的库存。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutTask {

    private final OrderService orderService;

    /** 超时时间（分钟），可在 application.yml 中通过 order.timeout-minutes 配置 */
    @Value("${order.timeout-minutes:30}")
    private int timeoutMinutes;

    /** 每 5 分钟扫描一次 */
    @Scheduled(fixedDelayString = "${order.timeout-scan-ms:300000}")
    public void cancelExpiredOrders() {
        try {
            orderService.autoCancelExpired(timeoutMinutes);
        } catch (Exception e) {
            log.error("自动取消超时订单失败", e);
        }
    }
}
