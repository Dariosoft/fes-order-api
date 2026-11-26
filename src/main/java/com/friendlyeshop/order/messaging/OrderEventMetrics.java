package com.friendlyeshop.order.messaging;

import com.friendlyeshop.order.model.enums.OrderStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OrderEventMetrics {

    private final Counter ordersCreated;

    private final Counter stockReserved;

    private final Counter stockRejected;

    private final Counter stockResultsIgnored;

    private final Counter stockResultsUnknown;

    private final Counter outboxPublished;

    private final Counter outboxPublishFailures;

    public OrderEventMetrics(MeterRegistry registry) {
        this.ordersCreated = Counter.builder("orders_created_total")
                .description("Orders accepted through POST /orders")
                .register(registry);
        this.stockReserved = Counter.builder("orders_stock_results_total")
                .tag("result", OrderStatus.STOCK_RESERVED.name())
                .register(registry);
        this.stockRejected = Counter.builder("orders_stock_results_total")
                .tag("result", OrderStatus.STOCK_REJECTED.name())
                .register(registry);
        this.stockResultsIgnored = Counter.builder("orders_stock_results_ignored_total")
                .description("Stock results ignored because the order already left PENDING")
                .register(registry);
        this.stockResultsUnknown = Counter.builder("orders_stock_results_unknown_total")
                .description("Stock results received for unknown orders")
                .register(registry);
        this.outboxPublished = Counter.builder("orders_outbox_published_total")
                .description("Outbox events acknowledged by the broker")
                .register(registry);
        this.outboxPublishFailures = Counter.builder("orders_outbox_publish_failures_total")
                .description("Outbox publish attempts that were not confirmed")
                .register(registry);
    }

    public void orderCreated() {
        ordersCreated.increment();
    }

    public void stockResult(OrderStatus targetStatus) {
        switch (targetStatus) {
            case STOCK_RESERVED -> stockReserved.increment();
            case STOCK_REJECTED -> stockRejected.increment();
            default -> throw new IllegalArgumentException(
                    "Not a stock result status: " + targetStatus);
        }
    }

    public void stockResultIgnored() {
        stockResultsIgnored.increment();
    }

    public void stockResultUnknown() {
        stockResultsUnknown.increment();
    }

    public void outboxPublished() {
        outboxPublished.increment();
    }

    public void outboxPublishFailed() {
        outboxPublishFailures.increment();
    }
}
