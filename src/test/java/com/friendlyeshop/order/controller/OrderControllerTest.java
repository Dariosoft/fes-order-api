package com.friendlyeshop.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.friendlyeshop.order.service.OrderService;
import org.junit.jupiter.api.Test;

class OrderControllerTest {

    @Test
    void identifiesService() {
        OrderController controller = new OrderController(mock(OrderService.class));

        assertThat(controller.orders()).containsEntry("service", "order-api");
    }
}
