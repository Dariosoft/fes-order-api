package com.friendlyeshop.order.messaging.rabbit.publisher;

public record PublishOutcome(boolean published, String reason) {

    public static PublishOutcome success() {
        return new PublishOutcome(true, null);
    }

    public static PublishOutcome failure(String reason) {
        return new PublishOutcome(false, reason);
    }
}
