package com.friendlyeshop.order.model.dto.in;

import com.friendlyeshop.order.constants.MessagingNames;
import com.friendlyeshop.order.exception.InvalidStockResultException;
import com.friendlyeshop.order.messaging.rabbit.StockResultMessage;
import com.friendlyeshop.order.model.enums.OrderStatus;
import java.time.Instant;
import java.util.UUID;

public record StockResultDetails(
        UUID eventId,
        String eventType,
        UUID orderId,
        Instant reservedAt,
        String reason,
        Instant rejectedAt) {

    public static StockResultDetails from(StockResultMessage message, String eventType) {
        return new StockResultDetails(
                message.eventId(),
                eventType,
                message.orderId(),
                message.reservedAt(),
                message.reason(),
                message.rejectedAt());
    }

    public OrderStatus targetStatus() {
        return switch (eventType) {
            case MessagingNames.STOCK_RESERVED_EVENT -> OrderStatus.STOCK_RESERVED;
            case MessagingNames.STOCK_REJECTED_EVENT -> OrderStatus.STOCK_REJECTED;
            default -> throw new InvalidStockResultException(
                    "Unsupported stock result event type: " + eventType);
        };
    }
}
