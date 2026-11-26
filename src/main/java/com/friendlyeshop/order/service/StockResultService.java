package com.friendlyeshop.order.service;

import com.friendlyeshop.order.messaging.OrderEventMetrics;
import com.friendlyeshop.order.model.dto.in.StockResultDetails;
import com.friendlyeshop.order.model.enums.OrderStatus;
import com.friendlyeshop.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockResultService {

    private static final Logger LOG = LoggerFactory.getLogger(StockResultService.class);

    private final OrderRepository orderRepository;

    private final OrderEventMetrics metrics;

    public StockResultService(OrderRepository orderRepository, OrderEventMetrics metrics) {
        this.orderRepository = orderRepository;
        this.metrics = metrics;
    }

    @Transactional
    public StockResultOutcome apply(StockResultDetails result) {
        OrderStatus targetStatus = result.targetStatus();
        int updated = orderRepository.transitionStatus(
                result.orderId(), OrderStatus.PENDING, targetStatus);
        if (updated > 0) {
            metrics.stockResult(targetStatus);
            LOG.info(
                    "Applied stock result eventType={} orderId={} status={} reason={}",
                    result.eventType(), result.orderId(), targetStatus, result.reason());
            return StockResultOutcome.APPLIED;
        }
        if (!orderRepository.existsById(result.orderId())) {
            metrics.stockResultUnknown();
            LOG.warn(
                    "Received stock result eventType={} for unknown orderId={}",
                    result.eventType(), result.orderId());
            return StockResultOutcome.UNKNOWN;
        }
        metrics.stockResultIgnored();
        LOG.info(
                "Ignored stock result eventType={} orderId={} because the order is not PENDING",
                result.eventType(), result.orderId());
        return StockResultOutcome.IGNORED;
    }
}
