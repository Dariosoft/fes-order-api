package com.friendlyeshop.order;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class OrderControllerTest {
    @Test
    void identifiesService() {
        assertThat(new OrderController().orders()).containsEntry("service", "order-api");
    }
}
