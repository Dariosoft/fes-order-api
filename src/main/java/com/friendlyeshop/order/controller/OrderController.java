package com.friendlyeshop.order.controller;

import com.friendlyeshop.order.model.dto.in.CreateOrderDetails;
import com.friendlyeshop.order.model.dto.out.OrderResponse;
import com.friendlyeshop.order.model.dto.request.CreateOrderRequest;
import com.friendlyeshop.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.create(CreateOrderDetails.from(request));
    }
}
