package com.friendlyeshop.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.friendlyeshop.order.model.dto.in.OrderItemDetails;
import com.friendlyeshop.order.model.enums.Currency;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderTotalCalculatorTest {

    private final OrderTotalCalculator calculator = new OrderTotalCalculator();

    @Test
    void sumsQuantityTimesAgreedPrice() {
        List<OrderItemDetails> items = List.of(
                item("10.00", 2),
                item("5.50", 1));

        assertThat(calculator.total(items)).isEqualByComparingTo("25.50");
    }

    @Test
    void roundsHalfUpToTwoDecimals() {
        List<OrderItemDetails> items = List.of(item("10.005", 3));

        assertThat(calculator.total(items)).isEqualByComparingTo("30.02");
    }

    @Test
    void returnsZeroWhenAllItemsAreFree() {
        List<OrderItemDetails> items = List.of(item("0.00", 5));

        assertThat(calculator.total(items)).isEqualByComparingTo("0.00");
    }

    private OrderItemDetails item(String unitPrice, int quantity) {
        return new OrderItemDetails(UUID.randomUUID(), quantity, new BigDecimal(unitPrice), Currency.ARS);
    }
}
