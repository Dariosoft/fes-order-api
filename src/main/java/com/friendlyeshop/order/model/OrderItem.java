package com.friendlyeshop.order.model;

import com.friendlyeshop.order.model.enums.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "order_items")
@Getter
public class OrderItem {

    @EmbeddedId
    private OrderItemId id;

    @MapsId("orderId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3)
    private Currency currency;

    protected OrderItem() {
    }

    public static OrderItem create(
            Order order, UUID productId, int quantity, BigDecimal unitPrice, Currency currency) {
        OrderItem item = new OrderItem();
        item.id = new OrderItemId(order.getId(), productId);
        item.order = order;
        item.quantity = quantity;
        item.unitPrice = unitPrice;
        item.currency = currency;
        return item;
    }
}
