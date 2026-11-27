package com.friendlyeshop.order.messaging.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.friendlyeshop.order.messaging.kafka.publisher.KafkaOrderPlacedPublisher;
import com.friendlyeshop.order.messaging.kafka.publisher.KafkaPublishOutcome;
import com.friendlyeshop.order.messaging.rabbit.codec.OrderPlacedMessageCodec;
import com.friendlyeshop.order.messaging.rabbit.model.OrderPlacedMessage;
import com.friendlyeshop.order.model.OrderOutbox;
import com.friendlyeshop.order.model.enums.Currency;
import com.friendlyeshop.order.model.enums.KafkaOutboxStatus;
import com.friendlyeshop.order.repository.OrderOutboxRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KafkaOutboxRelayTest {

    @Mock
    private OrderOutboxRepository outboxRepository;

    @Mock
    private KafkaOrderPlacedPublisher publisher;

    @Mock
    private OrderPlacedMessageCodec messageCodec;

    @Mock
    private KafkaOutboxMetrics metrics;

    private KafkaOutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new KafkaOutboxRelay(
                outboxRepository, publisher, messageCodec, metrics, 10, 1000);
    }

    @Test
    void marksEventPublishedAfterKafkaConfirmation() {
        OrderOutbox outbox = pendingOutbox();
        OrderPlacedMessage message = message();
        when(outboxRepository.findReadyForKafka(
                        eq(KafkaOutboxStatus.PENDING), any(), any()))
                .thenReturn(List.of(outbox));
        when(messageCodec.decode(outbox.getPayload())).thenReturn(message);
        when(publisher.publish(eq(message), any())).thenReturn(KafkaPublishOutcome.success());
        when(outboxRepository.markKafkaPublished(
                        eq(outbox.getEventId()), eq(KafkaOutboxStatus.PUBLISHED), any()))
                .thenReturn(1);

        relay.publishPending();

        verify(outboxRepository).markKafkaPublished(
                eq(outbox.getEventId()), eq(KafkaOutboxStatus.PUBLISHED), any());
        verify(metrics).published();
    }

    @Test
    void keepsEventPendingAndSchedulesRetryWhenKafkaFails() {
        OrderOutbox outbox = pendingOutbox();
        OrderPlacedMessage message = message();
        when(outboxRepository.findReadyForKafka(
                        eq(KafkaOutboxStatus.PENDING), any(), any()))
                .thenReturn(List.of(outbox));
        when(messageCodec.decode(outbox.getPayload())).thenReturn(message);
        when(publisher.publish(eq(message), any()))
                .thenReturn(KafkaPublishOutcome.failure("broker down"));

        relay.publishPending();

        verify(outboxRepository).recordKafkaFailure(eq(outbox.getEventId()), any());
        verify(metrics).publishFailed();
        verify(outboxRepository, never())
                .markKafkaPublished(any(), any(), any());
    }

    private static OrderOutbox pendingOutbox() {
        return OrderOutbox.pending(
                UUID.randomUUID(), "order.placed", UUID.randomUUID(), "{}", Instant.now());
    }

    private static OrderPlacedMessage message() {
        return new OrderPlacedMessage(
                UUID.randomUUID(),
                "order.placed",
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(),
                BigDecimal.TEN,
                Currency.ARS);
    }
}
