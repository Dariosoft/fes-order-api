package com.friendlyeshop.order.messaging.outbox;

import com.friendlyeshop.order.messaging.OrderEventMetrics;
import com.friendlyeshop.order.messaging.rabbit.codec.OrderPlacedMessageCodec;
import com.friendlyeshop.order.messaging.rabbit.model.OrderPlacedMessage;
import com.friendlyeshop.order.messaging.rabbit.publisher.OrderPlacedPublisher;
import com.friendlyeshop.order.messaging.rabbit.publisher.PublishOutcome;
import com.friendlyeshop.order.model.OrderOutbox;
import com.friendlyeshop.order.model.enums.OutboxStatus;
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
public class OutboxRelay {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxRelay.class);

    private final OrderOutboxRepository outboxRepository;

    private final OrderPlacedPublisher publisher;

    private final OrderPlacedMessageCodec messageCodec;

    private final OrderEventMetrics metrics;

    private final int batchSize;

    private final Duration confirmTimeout;

    public OutboxRelay(
            OrderOutboxRepository outboxRepository,
            OrderPlacedPublisher publisher,
            OrderPlacedMessageCodec messageCodec,
            OrderEventMetrics metrics,
            @Value("${app.outbox.batch-size:50}") int batchSize,
            @Value("${app.outbox.confirm-timeout-ms:5000}") long confirmTimeoutMs) {
        this.outboxRepository = outboxRepository;
        this.publisher = publisher;
        this.messageCodec = messageCodec;
        this.metrics = metrics;
        this.batchSize = batchSize;
        this.confirmTimeout = Duration.ofMillis(confirmTimeoutMs);
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:5000}")
    public void publishPending() {
        List<OrderOutbox> events = outboxRepository.findReadyToPublish(
                OutboxStatus.PENDING, Instant.now(), PageRequest.of(0, batchSize));
        events.forEach(this::publish);
    }

    private void publish(OrderOutbox event) {
        PublishOutcome outcome = attemptPublish(event);
        if (outcome.published()) {
            markPublished(event);
        } else {
            recordFailure(event, outcome.reason());
        }
    }

    private PublishOutcome attemptPublish(OrderOutbox event) {
        try {
            OrderPlacedMessage message = messageCodec.decode(event.getPayload());
            return publisher.publish(message, confirmTimeout);
        } catch (JacksonException exception) {
            return PublishOutcome.failure("invalid payload: " + exception.getMessage());
        }
    }

    private void markPublished(OrderOutbox event) {
        int updated = outboxRepository.markPublished(
                event.getEventId(), OutboxStatus.PUBLISHED, Instant.now());
        if (updated > 0) {
            metrics.outboxPublished();
            LOG.info(
                    "Published order.placed eventId={} orderId={}",
                    event.getEventId(), event.getAggregateId());
        }
    }

    private void recordFailure(OrderOutbox event, String reason) {
        int attempts = event.getAttempts() + 1;
        Instant nextAttemptAt = Instant.now().plus(OutboxBackoff.delayFor(attempts));
        outboxRepository.recordFailure(event.getEventId(), nextAttemptAt);
        metrics.outboxPublishFailed();
        LOG.warn(
                "Could not publish order.placed eventId={} orderId={} attempts={} reason={}",
                event.getEventId(), event.getAggregateId(), attempts, reason);
    }
}
