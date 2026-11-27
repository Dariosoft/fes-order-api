package com.friendlyeshop.order.messaging.kafka;

import com.friendlyeshop.order.messaging.kafka.publisher.KafkaOrderPlacedPublisher;
import com.friendlyeshop.order.messaging.kafka.publisher.KafkaPublishOutcome;
import com.friendlyeshop.order.messaging.outbox.OutboxBackoff;
import com.friendlyeshop.order.messaging.rabbit.codec.OrderPlacedMessageCodec;
import com.friendlyeshop.order.messaging.rabbit.model.OrderPlacedMessage;
import com.friendlyeshop.order.model.OrderOutbox;
import com.friendlyeshop.order.model.enums.KafkaOutboxStatus;
import com.friendlyeshop.order.repository.OrderOutboxRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;

@Component
public class KafkaOutboxRelay {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaOutboxRelay.class);

    private final OrderOutboxRepository outboxRepository;

    private final KafkaOrderPlacedPublisher publisher;

    private final OrderPlacedMessageCodec messageCodec;

    private final KafkaOutboxMetrics metrics;

    private final int batchSize;

    private final Duration sendTimeout;

    public KafkaOutboxRelay(
            OrderOutboxRepository outboxRepository,
            KafkaOrderPlacedPublisher publisher,
            OrderPlacedMessageCodec messageCodec,
            KafkaOutboxMetrics metrics,
            @Value("${app.kafka.batch-size:50}") int batchSize,
            @Value("${app.kafka.send-timeout-ms:5000}") long sendTimeoutMs) {
        this.outboxRepository = outboxRepository;
        this.publisher = publisher;
        this.messageCodec = messageCodec;
        this.metrics = metrics;
        this.batchSize = batchSize;
        this.sendTimeout = Duration.ofMillis(sendTimeoutMs);
    }

    @Scheduled(fixedDelayString = "${app.kafka.poll-interval-ms:5000}")
    public void publishPending() {
        List<OrderOutbox> events = outboxRepository.findReadyForKafka(
                KafkaOutboxStatus.PENDING, Instant.now(), PageRequest.of(0, batchSize));
        events.forEach(this::publish);
    }

    private void publish(OrderOutbox event) {
        KafkaPublishOutcome outcome = attemptPublish(event);
        if (outcome.published()) {
            markPublished(event);
        } else {
            recordFailure(event, outcome.reason());
        }
    }

    private KafkaPublishOutcome attemptPublish(OrderOutbox event) {
        try {
            OrderPlacedMessage message = messageCodec.decode(event.getPayload());
            return publisher.publish(message, sendTimeout);
        } catch (JacksonException exception) {
            return KafkaPublishOutcome.failure("invalid payload: " + exception.getMessage());
        }
    }

    private void markPublished(OrderOutbox event) {
        int updated = outboxRepository.markKafkaPublished(
                event.getEventId(), KafkaOutboxStatus.PUBLISHED, Instant.now());
        if (updated > 0) {
            metrics.published();
            LOG.info(
                    "Published event to Kafka eventId={} orderId={}",
                    event.getEventId(), event.getAggregateId());
        }
    }

    private void recordFailure(OrderOutbox event, String reason) {
        int attempts = event.getKafkaAttempts() + 1;
        Instant nextAttemptAt = Instant.now().plus(OutboxBackoff.delayFor(attempts));
        outboxRepository.recordKafkaFailure(event.getEventId(), nextAttemptAt);
        metrics.publishFailed();
        LOG.warn(
                "Could not publish event to Kafka eventId={} orderId={} attempts={} reason={}",
                event.getEventId(), event.getAggregateId(), attempts, reason);
    }
}
