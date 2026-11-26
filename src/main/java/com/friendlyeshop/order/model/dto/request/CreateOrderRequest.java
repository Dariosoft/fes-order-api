package com.friendlyeshop.order.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull(message = "El tenant es obligatorio")
        UUID tenantId,
        @NotEmpty(message = "El pedido debe tener al menos un ítem")
        @Valid
        List<OrderItemRequest> items) {
}
