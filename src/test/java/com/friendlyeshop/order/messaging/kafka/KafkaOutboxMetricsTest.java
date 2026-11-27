package com.friendlyeshop.order.messaging.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.friendlyeshop.order.model.enums.KafkaOutboxStatus;
import com.friendlyeshop.order.repository.OrderOutboxRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class KafkaOutboxMetricsTest {

    @Test
    void registersCountersAndPendingGauge() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OrderOutboxRepository repository = mock(OrderOutboxRepository.class);
        when(repository.countByKafkaStatus(KafkaOutboxStatus.PENDING)).thenReturn(3L);

        KafkaOutboxMetrics metrics = new KafkaOutboxMetrics(registry, repository);
        metrics.published();
        metrics.publishFailed();

        assertEquals(1.0, registry.get("orders_kafka_published_total").counter().count());
        assertEquals(1.0, registry.get("orders_kafka_publish_failures_total").counter().count());
        assertEquals(3.0, registry.get("orders_kafka_pending_count").gauge().value());
    }
}
