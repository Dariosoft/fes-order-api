package com.friendlyeshop.order.model.dto.in;

import com.friendlyeshop.order.model.dto.request.CreateOrderRequest;
import java.util.List;
import java.util.UUID;

public record CreateOrderDetails(UUID tenantId, List<OrderItemDetails> items) {

    public static CreateOrderDetails from(CreateOrderRequest request) {
        List<OrderItemDetails> itemDetails = request.items().stream()
                .map(OrderItemDetails::from)
                .toList();
        return new CreateOrderDetails(request.tenantId(), itemDetails);
    }
}
