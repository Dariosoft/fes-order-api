package com.friendlyeshop.order.controller;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {
    @GetMapping
    public Map<String, Object> orders() {
        return Map.of("service", "order-api", "status", "ready", "orders", List.of());
    }
}
