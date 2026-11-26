package com.friendlyeshop.order.messaging.rabbit;

import com.friendlyeshop.order.model.OrderItem;
import com.friendlyeshop.order.model.enums.Currency;
import java.math.BigDecimal;
import java.util.UUID;

public record OrderPlacedItemMessage(
        UUID productId,
        int quantity,
        BigDecimal unitPrice,
        Currency currency) {

    public static OrderPlacedItemMessage from(OrderItem item) {
        return new OrderPlacedItemMessage(
                item.getId().getProductId(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getCurrency());
    }
}
