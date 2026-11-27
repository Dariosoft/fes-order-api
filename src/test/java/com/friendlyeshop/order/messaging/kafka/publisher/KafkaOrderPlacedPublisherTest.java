package com.friendlyeshop.order.messaging.kafka.publisher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.friendlyeshop.order.messaging.rabbit.codec.OrderPlacedMessageCodec;
import com.friendlyeshop.order.messaging.rabbit.model.OrderPlacedMessage;
import com.friendlyeshop.order.model.enums.Currency;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class KafkaOrderPlacedPublisherTest {

    private static final String TOPIC = "orders.events";

    private static final String PAYLOAD = "{\"eventType\":\"order.placed\"}";

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private OrderPlacedMessageCodec messageCodec;

    private KafkaOrderPlacedPublisher publisher;

    private OrderPlacedMessage message;

    @BeforeEach
    void setUp() {
        publisher = new KafkaOrderPlacedPublisher(kafkaTemplate, messageCodec, TOPIC);
        message = new OrderPlacedMessage(
                UUID.randomUUID(),
                "order.placed",
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(),
                BigDecimal.TEN,
                Currency.ARS);
        when(messageCodec.encode(message)).thenReturn(PAYLOAD);
    }

    @Test
    void reportsSuccessWhenKafkaConfirms() {
        when(kafkaTemplate.send(TOPIC, message.orderId().toString(), PAYLOAD))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        KafkaPublishOutcome outcome = publisher.publish(message, Duration.ofSeconds(1));

        assertTrue(outcome.published());
    }

    @Test
    void reportsFailureWhenKafkaCompletesExceptionally() {
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("broker down"));
        when(kafkaTemplate.send(TOPIC, message.orderId().toString(), PAYLOAD)).thenReturn(failed);

        KafkaPublishOutcome outcome = publisher.publish(message, Duration.ofSeconds(1));

        assertFalse(outcome.published());
    }

    @Test
    void reportsFailureWhenTimeoutExpires() {
        when(kafkaTemplate.send(TOPIC, message.orderId().toString(), PAYLOAD))
                .thenReturn(new CompletableFuture<>());

        KafkaPublishOutcome outcome = publisher.publish(message, Duration.ofMillis(5));

        assertFalse(outcome.published());
    }

    @Test
    void reportsFailureWhenKafkaTemplateThrows() {
        when(kafkaTemplate.send(TOPIC, message.orderId().toString(), PAYLOAD))
                .thenThrow(new IllegalStateException("unavailable"));

        KafkaPublishOutcome outcome = publisher.publish(message, Duration.ofSeconds(1));

        assertFalse(outcome.published());
    }
}
