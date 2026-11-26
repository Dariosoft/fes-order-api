package com.friendlyeshop.order.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.friendlyeshop.order.model.enums.OutboxStatus;
import com.friendlyeshop.order.repository.OrderOutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class OutboxConfigTest {

    @Test
    void exposesPendingOutboxGauge() {
        OrderOutboxRepository repository = mock(OrderOutboxRepository.class);
        when(repository.countByStatus(OutboxStatus.PENDING)).thenReturn(3L);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        Gauge gauge = new OutboxConfig().pendingOutboxGauge(repository, registry);

        assertThat(registry.get("orders_outbox_pending_count").gauge().value()).isEqualTo(3.0);
        assertThat(gauge.value()).isEqualTo(3.0);
    }
}
