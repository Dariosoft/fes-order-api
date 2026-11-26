package com.friendlyeshop.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.friendlyeshop.order.constants.MessagingNames;
import com.friendlyeshop.order.messaging.OrderEventMetrics;
import com.friendlyeshop.order.model.dto.in.StockResultDetails;
import com.friendlyeshop.order.model.enums.OrderStatus;
import com.friendlyeshop.order.repository.OrderRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StockResultServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventMetrics metrics;

    private StockResultService stockResultService;

    @BeforeEach
    void setUp() {
        stockResultService = new StockResultService(orderRepository, metrics);
    }

    @Test
    void appliesReservedFromPending() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.transitionStatus(
                orderId, OrderStatus.PENDING, OrderStatus.STOCK_RESERVED)).thenReturn(1);

        StockResultOutcome outcome =
                stockResultService.apply(result(MessagingNames.STOCK_RESERVED_EVENT, orderId));

        assertThat(outcome).isEqualTo(StockResultOutcome.APPLIED);
        verify(metrics).stockResult(OrderStatus.STOCK_RESERVED);
        verify(orderRepository, never()).existsById(any());
    }

    @Test
    void appliesRejectedFromPending() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.transitionStatus(
                orderId, OrderStatus.PENDING, OrderStatus.STOCK_REJECTED)).thenReturn(1);

        StockResultOutcome outcome =
                stockResultService.apply(result(MessagingNames.STOCK_REJECTED_EVENT, orderId));

        assertThat(outcome).isEqualTo(StockResultOutcome.APPLIED);
        verify(metrics).stockResult(OrderStatus.STOCK_REJECTED);
    }

    @Test
    void appliesOnlyTheFirstTransitionWhenResultsArriveConcurrently() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.transitionStatus(
                orderId, OrderStatus.PENDING, OrderStatus.STOCK_RESERVED)).thenReturn(1, 0);
        when(orderRepository.existsById(orderId)).thenReturn(true);

        StockResultOutcome first =
                stockResultService.apply(result(MessagingNames.STOCK_RESERVED_EVENT, orderId));
        StockResultOutcome second =
                stockResultService.apply(result(MessagingNames.STOCK_RESERVED_EVENT, orderId));

        assertThat(first).isEqualTo(StockResultOutcome.APPLIED);
        assertThat(second).isEqualTo(StockResultOutcome.IGNORED);
        verify(metrics).stockResult(OrderStatus.STOCK_RESERVED);
        verify(metrics).stockResultIgnored();
    }

    @Test
    void ignoresContradictoryResultAfterTheFirstTransition() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.transitionStatus(
                orderId, OrderStatus.PENDING, OrderStatus.STOCK_REJECTED)).thenReturn(1);
        when(orderRepository.transitionStatus(
                orderId, OrderStatus.PENDING, OrderStatus.STOCK_RESERVED)).thenReturn(0);
        when(orderRepository.existsById(orderId)).thenReturn(true);

        stockResultService.apply(result(MessagingNames.STOCK_REJECTED_EVENT, orderId));
        StockResultOutcome lateReservation =
                stockResultService.apply(result(MessagingNames.STOCK_RESERVED_EVENT, orderId));

        assertThat(lateReservation).isEqualTo(StockResultOutcome.IGNORED);
        verify(metrics).stockResult(OrderStatus.STOCK_REJECTED);
        verify(metrics, never()).stockResult(OrderStatus.STOCK_RESERVED);
    }

    @Test
    void reportsUnknownOrderWithoutUpdatingAnything() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.transitionStatus(any(), any(), any())).thenReturn(0);
        when(orderRepository.existsById(orderId)).thenReturn(false);

        StockResultOutcome outcome =
                stockResultService.apply(result(MessagingNames.STOCK_RESERVED_EVENT, orderId));

        assertThat(outcome).isEqualTo(StockResultOutcome.UNKNOWN);
        verify(metrics).stockResultUnknown();
        verify(metrics, never()).stockResultIgnored();
    }

    private StockResultDetails result(String eventType, UUID orderId) {
        return new StockResultDetails(
                UUID.randomUUID(), eventType, orderId, Instant.now(), "reason", Instant.now());
    }
}
