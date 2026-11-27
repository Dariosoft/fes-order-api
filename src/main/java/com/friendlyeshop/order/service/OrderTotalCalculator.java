package com.friendlyeshop.order.service;

import com.friendlyeshop.order.model.dto.in.OrderItemDetails;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OrderTotalCalculator {

    private static final int MONEY_SCALE = 2;

    public BigDecimal total(List<OrderItemDetails> items) {
        return items.stream()
                .map(OrderItemDetails::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
