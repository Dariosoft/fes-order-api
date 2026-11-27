package com.friendlyeshop.order.service;

import com.friendlyeshop.order.exception.InvalidOrderException;
import com.friendlyeshop.order.messaging.OrderEventMetrics;
import com.friendlyeshop.order.messaging.rabbit.codec.OrderPlacedMessageCodec;
import com.friendlyeshop.order.messaging.rabbit.model.OrderPlacedMessage;
import com.friendlyeshop.order.model.Order;
import com.friendlyeshop.order.model.OrderItem;
import com.friendlyeshop.order.model.OrderOutbox;
import com.friendlyeshop.order.model.dto.in.CreateOrderDetails;
import com.friendlyeshop.order.model.dto.in.OrderItemDetails;
import com.friendlyeshop.order.model.dto.out.OrderResponse;
import com.friendlyeshop.order.model.enums.Currency;
import com.friendlyeshop.order.repository.OrderOutboxRepository;
import com.friendlyeshop.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;

    private final OrderOutboxRepository orderOutboxRepository;

    private final OrderTotalCalculator totalCalculator;

    private final OrderPlacedMessageCodec messageCodec;

    private final OrderEventMetrics metrics;

    public OrderService(
            OrderRepository orderRepository,
            OrderOutboxRepository orderOutboxRepository,
            OrderTotalCalculator totalCalculator,
            OrderPlacedMessageCodec messageCodec,
            OrderEventMetrics metrics) {
        this.orderRepository = orderRepository;
        this.orderOutboxRepository = orderOutboxRepository;
        this.totalCalculator = totalCalculator;
        this.messageCodec = messageCodec;
        this.metrics = metrics;
    }

    @Transactional
    public OrderResponse create(CreateOrderDetails details) {
        Currency currency = requireSingleCurrency(details.items());
        requireUniqueProducts(details.items());
        BigDecimal total = totalCalculator.total(details.items());
        Instant now = Instant.now();
        Order order = Order.create(details.tenantId(), total, currency, now);
        details.items().forEach(item -> order.addItem(OrderItem.create(
                order, item.productId(), item.quantity(), item.unitPrice(), item.currency())));
        orderRepository.save(order);
        saveOrderPlacedOutbox(order, now);
        metrics.orderCreated();
        return OrderResponse.from(order);
    }

    private void saveOrderPlacedOutbox(Order order, Instant occurredAt) {
        UUID eventId = UUID.randomUUID();
        OrderPlacedMessage message = OrderPlacedMessage.from(order, eventId);
        OrderOutbox outbox = OrderOutbox.pending(
                eventId, message.eventType(), order.getId(), messageCodec.encode(message), occurredAt);
        orderOutboxRepository.save(outbox);
    }

    private static Currency requireSingleCurrency(List<OrderItemDetails> items) {
        Currency currency = items.getFirst().currency();
        boolean mixed = items.stream().anyMatch(item -> item.currency() != currency);
        if (mixed) {
            throw new InvalidOrderException("Todos los ítems del pedido deben usar la misma moneda");
        }
        return currency;
    }

    private static void requireUniqueProducts(List<OrderItemDetails> items) {
        long distinctProducts = items.stream().map(OrderItemDetails::productId).distinct().count();
        if (distinctProducts != items.size()) {
            throw new InvalidOrderException("El pedido no puede repetir el mismo producto");
        }
    }
}
