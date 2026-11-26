package com.friendlyeshop.order.config;

import com.friendlyeshop.order.model.enums.OutboxStatus;
import com.friendlyeshop.order.repository.OrderOutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class OutboxConfig {

    @Bean
    public Gauge pendingOutboxGauge(
            OrderOutboxRepository outboxRepository, MeterRegistry meterRegistry) {
        return Gauge.builder(
                        "orders_outbox_pending_count",
                        outboxRepository,
                        repository -> repository.countByStatus(OutboxStatus.PENDING))
                .description("Outbox events waiting to be published")
                .register(meterRegistry);
    }
}
