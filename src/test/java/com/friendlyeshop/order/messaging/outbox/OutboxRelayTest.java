package com.friendlyeshop.order.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.friendlyeshop.order.constants.MessagingNames;
import com.friendlyeshop.order.messaging.OrderEventMetrics;
import com.friendlyeshop.order.messaging.rabbit.codec.OrderPlacedMessageCodec;
import com.friendlyeshop.order.messaging.rabbit.model.OrderPlacedItemMessage;
import com.friendlyeshop.order.messaging.rabbit.model.OrderPlacedMessage;
import com.friendlyeshop.order.messaging.rabbit.publisher.OrderPlacedPublisher;
import com.friendlyeshop.order.messaging.rabbit.publisher.PublishOutcome;
import com.friendlyeshop.order.model.OrderOutbox;
import com.friendlyeshop.order.model.enums.Currency;
import com.friendlyeshop.order.model.enums.OutboxStatus;
import com.friendlyeshop.order.repository.OrderOutboxRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.json.JsonMapper;

class OutboxRelayTest {

    private final OrderOutboxRepository outboxRepository = mock(OrderOutboxRepository.class);

    private final OrderPlacedPublisher publisher = mock(OrderPlacedPublisher.class);

    private final OrderEventMetrics metrics = mock(OrderEventMetrics.class);

    private final OrderPlacedMessageCodec messageCodec =
            new OrderPlacedMessageCodec(JsonMapper.builder().build());

    private OutboxRelay relay;

    private OrderOutbox outbox;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(outboxRepository, publisher, messageCodec, metrics, 50, 5000);
        outbox = OrderOutbox.pending(
                UUID.randomUUID(),
                MessagingNames.ORDER_PLACED_EVENT,
                UUID.randomUUID(),
                messageCodec.encode(event()),
                Instant.now());
        when(outboxRepository.findReadyToPublish(
                eq(OutboxStatus.PENDING), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(outbox));
    }

    @Test
    void marksEventPublishedOnlyAfterBrokerAck() {
        when(publisher.publish(any(OrderPlacedMessage.class), any(Duration.class)))
                .thenReturn(PublishOutcome.success());
        when(outboxRepository.markPublished(any(), any(), any())).thenReturn(1);

        relay.publishPending();

        verify(outboxRepository).markPublished(
                eq(outbox.getEventId()), eq(OutboxStatus.PUBLISHED), any(Instant.class));
        verify(metrics).outboxPublished();
        verify(outboxRepository, never()).recordFailure(any(), any());
        verify(outboxRepository, never()).deleteById(any(UUID.class));
    }

    @Test
    void keepsEventPendingAndIncrementsAttemptsOnFailure() {
        when(publisher.publish(any(OrderPlacedMessage.class), any(Duration.class)))
                .thenReturn(PublishOutcome.failure("nack"));

        relay.publishPending();

        ArgumentCaptor<Instant> nextAttemptCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(outboxRepository).recordFailure(eq(outbox.getEventId()), nextAttemptCaptor.capture());
        assertThat(nextAttemptCaptor.getValue()).isAfter(Instant.now());
        verify(metrics).outboxPublishFailed();
        verify(outboxRepository, never()).markPublished(any(), any(), any());
        verify(outboxRepository, never()).deleteById(any(UUID.class));
    }

    @Test
    void treatsInvalidPayloadAsFailureWithoutPublishing() {
        outbox = OrderOutbox.pending(
                UUID.randomUUID(),
                MessagingNames.ORDER_PLACED_EVENT,
                UUID.randomUUID(),
                "not-json",
                Instant.now());
        when(outboxRepository.findReadyToPublish(
                eq(OutboxStatus.PENDING), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(outbox));

        relay.publishPending();

        verify(publisher, never()).publish(any(OrderPlacedMessage.class), any(Duration.class));
        verify(outboxRepository).recordFailure(eq(outbox.getEventId()), any(Instant.class));
        verify(metrics).outboxPublishFailed();
        verify(outboxRepository, never()).deleteById(any(UUID.class));
    }

    private OrderPlacedMessage event() {
        return new OrderPlacedMessage(
                UUID.randomUUID(),
                MessagingNames.ORDER_PLACED_EVENT,
                Instant.parse("2026-11-25T12:00:00Z"),
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(new OrderPlacedItemMessage(
                        UUID.randomUUID(), 1, new BigDecimal("10.00"), Currency.ARS)),
                new BigDecimal("10.00"),
                Currency.ARS);
    }
}
