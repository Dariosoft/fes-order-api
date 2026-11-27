package com.friendlyeshop.order.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MessagingNames {

    public static final String ORDERS_EXCHANGE = "orders";

    public static final String ORDERS_TOPIC = "orders.events";

    public static final String ORDER_PLACED_ROUTING_KEY = "order.placed";

    public static final String ORDER_PLACED_EVENT = "order.placed";

    public static final String STOCK_RESULTS_QUEUE = "order.stock-results";

    public static final String STOCK_RESULTS_RETRY_QUEUE = "order.stock-results.retry";

    public static final String STOCK_RESULTS_DLQ = "order.stock-results.dlq";

    public static final String STOCK_RESERVED_EVENT = "stock.reserved";

    public static final String STOCK_REJECTED_EVENT = "stock.rejected";
}
