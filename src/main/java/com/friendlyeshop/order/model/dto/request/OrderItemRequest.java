package com.friendlyeshop.order.model.dto.request;

import com.friendlyeshop.order.model.enums.Currency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemRequest(
        @NotNull(message = "El producto es obligatorio")
        UUID productId,
        @NotNull(message = "La cantidad es obligatoria")
        @Min(value = 1, message = "La cantidad debe ser mayor que 0")
        Integer quantity,
        @NotNull(message = "El precio unitario es obligatorio")
        @DecimalMin(value = "0.00", message = "El precio unitario no puede ser negativo")
        BigDecimal unitPrice,
        @NotNull(message = "La moneda es obligatoria")
        Currency currency) {
}
