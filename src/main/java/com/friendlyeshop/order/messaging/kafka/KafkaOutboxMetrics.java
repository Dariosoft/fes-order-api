package com.friendlyeshop.order.messaging.kafka;

import com.friendlyeshop.order.model.enums.KafkaOutboxStatus;
import com.friendlyeshop.order.repository.OrderOutboxRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class KafkaOutboxMetrics {

    private final Counter published;

    private final Counter publishFailures;

    public KafkaOutboxMetrics(MeterRegistry registry, OrderOutboxRepository outboxRepository) {
        this.published = Counter.builder("orders_kafka_published_total")
                .description("Outbox events acknowledged by Kafka")
                .register(registry);
        this.publishFailures = Counter.builder("orders_kafka_publish_failures_total")
                .description("Outbox Kafka publish attempts that were not confirmed")
                .register(registry);
        Gauge.builder(
                        "orders_kafka_pending_count",
                        outboxRepository,
                        repository -> repository.countByKafkaStatus(KafkaOutboxStatus.PENDING))
                .description("Outbox events still pending publication to Kafka")
                .register(registry);
    }

    public void published() {
        published.increment();
    }

    public void publishFailed() {
        publishFailures.increment();
    }
}
