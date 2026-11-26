package com.friendlyeshop.order.model.dto.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.friendlyeshop.order.constants.MessagingNames;
import com.friendlyeshop.order.exception.InvalidStockResultException;
import com.friendlyeshop.order.messaging.rabbit.StockResultMessage;
import com.friendlyeshop.order.model.enums.OrderStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StockResultDetailsTest {

    private final StockResultMessage message = new StockResultMessage(
            UUID.randomUUID(), UUID.randomUUID(), Instant.now(), null, null);

    @Test
    void mapsStockReservedRoutingKeyToStockReservedStatus() {
        StockResultDetails details =
                StockResultDetails.from(message, MessagingNames.STOCK_RESERVED_EVENT);

        assertThat(details.targetStatus()).isEqualTo(OrderStatus.STOCK_RESERVED);
    }

    @Test
    void mapsStockRejectedRoutingKeyToStockRejectedStatus() {
        StockResultDetails details =
                StockResultDetails.from(message, MessagingNames.STOCK_REJECTED_EVENT);

        assertThat(details.targetStatus()).isEqualTo(OrderStatus.STOCK_REJECTED);
    }

    @Test
    void rejectsUnsupportedRoutingKey() {
        assertThatThrownBy(() -> StockResultDetails.from(message, "stock.unknown").targetStatus())
                .isInstanceOf(InvalidStockResultException.class);
    }
}
