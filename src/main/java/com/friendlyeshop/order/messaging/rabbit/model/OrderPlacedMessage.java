package com.friendlyeshop.order.messaging.rabbit.model;

import com.friendlyeshop.order.constants.MessagingNames;
import com.friendlyeshop.order.model.Order;
import com.friendlyeshop.order.model.enums.Currency;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderPlacedMessage(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID orderId,
        UUID tenantId,
        List<OrderPlacedItemMessage> items,
        BigDecimal total,
        Currency currency) {

    public static OrderPlacedMessage from(Order order, UUID eventId) {
        List<OrderPlacedItemMessage> itemMessages = order.getItems().stream()
                .map(OrderPlacedItemMessage::from)
                .toList();
        return new OrderPlacedMessage(
                eventId,
                MessagingNames.ORDER_PLACED_EVENT,
                order.getCreatedAt(),
                order.getId(),
                order.getTenantId(),
                itemMessages,
                order.getTotal(),
                order.getCurrency());
    }
}
