package com.friendlyeshop.order.messaging.rabbit.model;

import java.time.Instant;
import java.util.UUID;

public record StockResultMessage(
        UUID eventId,
        UUID orderId,
        Instant reservedAt,
        String reason,
        Instant rejectedAt) {
}
