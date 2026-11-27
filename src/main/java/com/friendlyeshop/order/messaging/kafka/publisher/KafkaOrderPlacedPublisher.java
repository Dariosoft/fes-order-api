package com.friendlyeshop.order.messaging.kafka.publisher;

import com.friendlyeshop.order.constants.MessagingNames;
import com.friendlyeshop.order.messaging.rabbit.codec.OrderPlacedMessageCodec;
import com.friendlyeshop.order.messaging.rabbit.model.OrderPlacedMessage;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaOrderPlacedPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    private final OrderPlacedMessageCodec messageCodec;

    private final String topic;

    public KafkaOrderPlacedPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            OrderPlacedMessageCodec messageCodec,
            @Value("${app.kafka.topic:" + MessagingNames.ORDERS_TOPIC + "}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.messageCodec = messageCodec;
        this.topic = topic;
    }

    public KafkaPublishOutcome publish(OrderPlacedMessage event, Duration sendTimeout) {
        try {
            kafkaTemplate
                    .send(topic, event.orderId().toString(), messageCodec.encode(event))
                    .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
            return KafkaPublishOutcome.success();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return KafkaPublishOutcome.failure("interrupted while awaiting Kafka confirmation");
        } catch (ExecutionException | TimeoutException exception) {
            return KafkaPublishOutcome.failure("kafka publish failed: " + exception.getMessage());
        } catch (RuntimeException exception) {
            return KafkaPublishOutcome.failure("kafka unavailable: " + exception.getMessage());
        }
    }
}
