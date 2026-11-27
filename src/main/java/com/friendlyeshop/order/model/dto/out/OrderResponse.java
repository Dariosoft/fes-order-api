package com.friendlyeshop.order.model.dto.out;

import com.friendlyeshop.order.model.Order;
import com.friendlyeshop.order.model.enums.Currency;
import com.friendlyeshop.order.model.enums.OrderStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        OrderStatus status,
        BigDecimal total,
        Currency currency) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(), order.getStatus(), order.getTotal(), order.getCurrency());
    }
}
