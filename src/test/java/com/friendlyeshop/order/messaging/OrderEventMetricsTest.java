package com.friendlyeshop.order.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.friendlyeshop.order.model.enums.OrderStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class OrderEventMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private final OrderEventMetrics metrics = new OrderEventMetrics(registry);

    @Test
    void exposesOrderAndStockCounters() {
        metrics.orderCreated();
        metrics.stockResult(OrderStatus.STOCK_RESERVED);
        metrics.stockResult(OrderStatus.STOCK_REJECTED);
        metrics.stockResultIgnored();
        metrics.stockResultUnknown();
        metrics.outboxPublished();
        metrics.outboxPublishFailed();

        assertThat(counter("orders_created_total")).isEqualTo(1.0);
        assertThat(counter("orders_stock_results_total", "result", "STOCK_RESERVED")).isEqualTo(1.0);
        assertThat(counter("orders_stock_results_total", "result", "STOCK_REJECTED")).isEqualTo(1.0);
        assertThat(counter("orders_stock_results_ignored_total")).isEqualTo(1.0);
        assertThat(counter("orders_stock_results_unknown_total")).isEqualTo(1.0);
        assertThat(counter("orders_outbox_published_total")).isEqualTo(1.0);
        assertThat(counter("orders_outbox_publish_failures_total")).isEqualTo(1.0);
    }

    private double counter(String name, String... tags) {
        return registry.get(name).tags(tags).counter().count();
    }
}
