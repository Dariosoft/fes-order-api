package com.friendlyeshop.order.messaging.rabbit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.friendlyeshop.order.constants.MessagingNames;
import com.friendlyeshop.order.model.enums.Currency;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.json.JsonMapper;

class OrderPlacedPublisherTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);

    private final OrderPlacedMessageCodec messageCodec =
            new OrderPlacedMessageCodec(JsonMapper.builder().build());

    private OrderPlacedPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OrderPlacedPublisher(rabbitTemplate, messageCodec);
    }

    @Test
    void publishesFullPayloadToOrdersExchange() {
        OrderPlacedMessage event = event();
        completeSend(new CorrelationData.Confirm(true, null), null);

        PublishOutcome outcome = publisher.publish(event, Duration.ofSeconds(1));

        assertThat(outcome.published()).isTrue();
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(
                eq(MessagingNames.ORDERS_EXCHANGE),
                eq(MessagingNames.ORDER_PLACED_ROUTING_KEY),
                messageCaptor.capture(),
                any(CorrelationData.class));

        Message sent = messageCaptor.getValue();
        assertThat(sent.getMessageProperties().getMessageId()).isEqualTo(event.eventId().toString());
        assertThat(sent.getMessageProperties().getContentType()).isEqualTo("application/json");

        OrderPlacedMessage decoded =
                messageCodec.decode(new String(sent.getBody(), StandardCharsets.UTF_8));
        assertThat(decoded.eventId()).isEqualTo(event.eventId());
        assertThat(decoded.eventType()).isEqualTo(MessagingNames.ORDER_PLACED_EVENT);
        assertThat(decoded.occurredAt()).isEqualTo(event.occurredAt());
        assertThat(decoded.orderId()).isEqualTo(event.orderId());
        assertThat(decoded.tenantId()).isEqualTo(event.tenantId());
        assertThat(decoded.items()).hasSize(1);
        assertThat(decoded.items().getFirst().productId())
                .isEqualTo(event.items().getFirst().productId());
        assertThat(decoded.total()).isEqualByComparingTo(event.total());
        assertThat(decoded.currency()).isEqualTo(event.currency());
    }

    @Test
    void reportsFailureWhenBrokerNacks() {
        completeSend(new CorrelationData.Confirm(false, "nack"), null);

        PublishOutcome outcome = publisher.publish(event(), Duration.ofSeconds(1));

        assertThat(outcome.published()).isFalse();
        assertThat(outcome.reason()).contains("nack");
    }

    @Test
    void reportsFailureWhenMessageIsReturned() {
        ReturnedMessage returned =
                new ReturnedMessage(new Message(new byte[0]), 312, "NO_ROUTE", "orders", "order.placed");
        completeSend(new CorrelationData.Confirm(true, null), returned);

        PublishOutcome outcome = publisher.publish(event(), Duration.ofSeconds(1));

        assertThat(outcome.published()).isFalse();
        assertThat(outcome.reason()).contains("returned");
    }

    private void completeSend(CorrelationData.Confirm confirm, ReturnedMessage returned) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            if (returned != null) {
                correlation.setReturned(returned);
            }
            correlation.getFuture().complete(confirm);
            return null;
        }).when(rabbitTemplate).send(
                anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    private OrderPlacedMessage event() {
        return new OrderPlacedMessage(
                UUID.randomUUID(),
                MessagingNames.ORDER_PLACED_EVENT,
                Instant.parse("2026-11-25T12:00:00Z"),
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(new OrderPlacedItemMessage(
                        UUID.randomUUID(), 2, new BigDecimal("10.00"), Currency.ARS)),
                new BigDecimal("20.00"),
                Currency.ARS);
    }
}
