package com.friendlyeshop.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.friendlyeshop.order.constants.MessagingNames;
import com.friendlyeshop.order.exception.InvalidOrderException;
import com.friendlyeshop.order.messaging.OrderEventMetrics;
import com.friendlyeshop.order.messaging.rabbit.OrderPlacedMessage;
import com.friendlyeshop.order.messaging.rabbit.OrderPlacedMessageCodec;
import com.friendlyeshop.order.model.Order;
import com.friendlyeshop.order.model.OrderOutbox;
import com.friendlyeshop.order.model.dto.in.CreateOrderDetails;
import com.friendlyeshop.order.model.dto.in.OrderItemDetails;
import com.friendlyeshop.order.model.dto.out.OrderResponse;
import com.friendlyeshop.order.model.enums.Currency;
import com.friendlyeshop.order.model.enums.OrderStatus;
import com.friendlyeshop.order.model.enums.OutboxStatus;
import com.friendlyeshop.order.repository.OrderOutboxRepository;
import com.friendlyeshop.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderOutboxRepository orderOutboxRepository;

    @Mock
    private OrderEventMetrics metrics;

    private OrderPlacedMessageCodec messageCodec;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        messageCodec = new OrderPlacedMessageCodec(JsonMapper.builder().build());
        orderService = new OrderService(
                orderRepository,
                orderOutboxRepository,
                new OrderTotalCalculator(),
                messageCodec,
                metrics);
    }

    @Test
    void createsPendingOrderWithItemsAndPendingOutbox() {
        UUID tenantId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        CreateOrderDetails details = new CreateOrderDetails(
                tenantId,
                List.of(new OrderItemDetails(productId, 2, new BigDecimal("10.00"), Currency.ARS)));

        OrderResponse response = orderService.create(details);

        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.total()).isEqualByComparingTo("20.00");
        assertThat(response.currency()).isEqualTo(Currency.ARS);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(savedOrder.getItems()).hasSize(1);
        assertThat(savedOrder.getItems().getFirst().getQuantity()).isEqualTo(2);
        assertThat(savedOrder.getTotal()).isEqualByComparingTo("20.00");

        ArgumentCaptor<OrderOutbox> outboxCaptor = ArgumentCaptor.forClass(OrderOutbox.class);
        verify(orderOutboxRepository).save(outboxCaptor.capture());
        OrderOutbox outbox = outboxCaptor.getValue();
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outbox.getEventType()).isEqualTo(MessagingNames.ORDER_PLACED_EVENT);
        assertThat(outbox.getAggregateId()).isEqualTo(savedOrder.getId());

        OrderPlacedMessage message = messageCodec.decode(outbox.getPayload());
        assertThat(message.orderId()).isEqualTo(savedOrder.getId());
        assertThat(message.tenantId()).isEqualTo(tenantId);
        assertThat(message.items()).hasSize(1);
        assertThat(message.items().getFirst().productId()).isEqualTo(productId);
        assertThat(message.total()).isEqualByComparingTo("20.00");
        assertThat(message.currency()).isEqualTo(Currency.ARS);
        verify(metrics).orderCreated();
    }

    @Test
    void rejectsMixedCurrenciesWithoutPersisting() {
        CreateOrderDetails details = new CreateOrderDetails(
                UUID.randomUUID(),
                List.of(
                        item(UUID.randomUUID(), Currency.ARS),
                        item(UUID.randomUUID(), Currency.USD)));

        assertThatThrownBy(() -> orderService.create(details))
                .isInstanceOf(InvalidOrderException.class);
        verifyNoInteractions(orderRepository, orderOutboxRepository, metrics);
    }

    @Test
    void rejectsDuplicateProductsWithoutPersisting() {
        UUID productId = UUID.randomUUID();
        CreateOrderDetails details = new CreateOrderDetails(
                UUID.randomUUID(),
                List.of(item(productId, Currency.ARS), item(productId, Currency.ARS)));

        assertThatThrownBy(() -> orderService.create(details))
                .isInstanceOf(InvalidOrderException.class);
        verifyNoInteractions(orderRepository, orderOutboxRepository, metrics);
    }

    @Test
    void propagatesOutboxFailure() {
        when(orderOutboxRepository.save(any(OrderOutbox.class)))
                .thenThrow(new IllegalStateException("db down"));
        CreateOrderDetails details = new CreateOrderDetails(
                UUID.randomUUID(),
                List.of(item(UUID.randomUUID(), Currency.ARS)));

        assertThatThrownBy(() -> orderService.create(details))
                .isInstanceOf(IllegalStateException.class);
        verify(orderRepository).save(any(Order.class));
    }

    private OrderItemDetails item(UUID productId, Currency currency) {
        return new OrderItemDetails(productId, 1, new BigDecimal("10.00"), currency);
    }
}
