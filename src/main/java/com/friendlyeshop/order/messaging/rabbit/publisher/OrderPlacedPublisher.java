package com.friendlyeshop.order.messaging.rabbit.publisher;

import com.friendlyeshop.order.constants.MessagingNames;
import com.friendlyeshop.order.messaging.rabbit.codec.OrderPlacedMessageCodec;
import com.friendlyeshop.order.messaging.rabbit.model.OrderPlacedMessage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderPlacedPublisher {

    private static final String CONTENT_TYPE = "application/json";

    private static final String CONTENT_ENCODING = "UTF-8";

    private final RabbitTemplate rabbitTemplate;

    private final OrderPlacedMessageCodec messageCodec;

    public OrderPlacedPublisher(RabbitTemplate rabbitTemplate, OrderPlacedMessageCodec messageCodec) {
        this.rabbitTemplate = rabbitTemplate;
        this.messageCodec = messageCodec;
    }

    public PublishOutcome publish(OrderPlacedMessage event, Duration confirmTimeout) {
        CorrelationData correlation = new CorrelationData(event.eventId().toString());
        Message message = buildMessage(event);
        try {
            rabbitTemplate.send(
                    MessagingNames.ORDERS_EXCHANGE,
                    MessagingNames.ORDER_PLACED_ROUTING_KEY,
                    message,
                    correlation);
            return awaitConfirmation(correlation, confirmTimeout);
        } catch (AmqpException exception) {
            return PublishOutcome.failure("broker unavailable: " + exception.getMessage());
        }
    }

    private PublishOutcome awaitConfirmation(CorrelationData correlation, Duration confirmTimeout) {
        try {
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                return PublishOutcome.failure("nack: " + confirm.getReason());
            }
            if (correlation.getReturned() != null) {
                return PublishOutcome.failure("returned: " + correlation.getReturned().getReplyText());
            }
            return PublishOutcome.success();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return PublishOutcome.failure("interrupted while awaiting confirmation");
        } catch (ExecutionException | TimeoutException exception) {
            return PublishOutcome.failure("confirmation failed: " + exception.getMessage());
        }
    }

    private Message buildMessage(OrderPlacedMessage event) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(CONTENT_TYPE);
        properties.setContentEncoding(CONTENT_ENCODING);
        properties.setMessageId(event.eventId().toString());
        properties.setType(event.eventType());
        byte[] body = messageCodec.encode(event).getBytes(StandardCharsets.UTF_8);
        return new Message(body, properties);
    }
}
