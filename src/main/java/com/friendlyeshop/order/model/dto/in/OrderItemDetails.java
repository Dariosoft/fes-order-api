package com.friendlyeshop.order.model.dto.in;

import com.friendlyeshop.order.model.dto.request.OrderItemRequest;
import com.friendlyeshop.order.model.enums.Currency;
import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemDetails(
        UUID productId,
        int quantity,
        BigDecimal unitPrice,
        Currency currency) {

    public static OrderItemDetails from(OrderItemRequest request) {
        return new OrderItemDetails(
                request.productId(), request.quantity(), request.unitPrice(), request.currency());
    }

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
