package com.friendlyeshop.order.messaging.kafka.publisher;

public record KafkaPublishOutcome(boolean published, String reason) {

    public static KafkaPublishOutcome success() {
        return new KafkaPublishOutcome(true, null);
    }

    public static KafkaPublishOutcome failure(String reason) {
        return new KafkaPublishOutcome(false, reason);
    }
}
