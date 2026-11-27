package com.friendlyeshop.order.model;

import com.friendlyeshop.order.model.enums.KafkaOutboxStatus;
import com.friendlyeshop.order.model.enums.OutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "order_outbox")
@Getter
public class OrderOutbox {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "kafka_status", nullable = false, length = 20)
    private KafkaOutboxStatus kafkaStatus;

    @Column(name = "kafka_published_at")
    private Instant kafkaPublishedAt;

    @Column(name = "kafka_attempts", nullable = false)
    private int kafkaAttempts;

    @Column(name = "kafka_next_attempt_at", nullable = false)
    private Instant kafkaNextAttemptAt;

    protected OrderOutbox() {
    }

    public static OrderOutbox pending(
            UUID eventId, String eventType, UUID aggregateId, String payload, Instant occurredAt) {
        OrderOutbox outbox = new OrderOutbox();
        outbox.eventId = eventId;
        outbox.eventType = eventType;
        outbox.aggregateId = aggregateId;
        outbox.payload = payload;
        outbox.status = OutboxStatus.PENDING;
        outbox.occurredAt = occurredAt;
        outbox.createdAt = occurredAt;
        outbox.attempts = 0;
        outbox.nextAttemptAt = occurredAt;
        outbox.kafkaStatus = KafkaOutboxStatus.PENDING;
        outbox.kafkaAttempts = 0;
        outbox.kafkaNextAttemptAt = occurredAt;
        return outbox;
    }
}
