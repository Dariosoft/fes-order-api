package com.friendlyeshop.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.friendlyeshop.order.exception.InvalidOrderException;
import com.friendlyeshop.order.model.dto.in.CreateOrderDetails;
import com.friendlyeshop.order.model.dto.out.OrderResponse;
import com.friendlyeshop.order.model.enums.Currency;
import com.friendlyeshop.order.model.enums.OrderStatus;
import com.friendlyeshop.order.service.OrderService;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
class OrderControllerWebTest {

    private static final String TENANT_ID = "11111111-1111-1111-1111-111111111111";

    private static final String PRODUCT_ID = "22222222-2222-2222-2222-222222222222";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    void rejectsGetOnPurchasesRoot() throws Exception {
        mockMvc.perform(get("/orders/purchases")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void doesNotServeTheControllerWithoutThePrefix() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createsOrderAndReturnsCreatedContract() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.create(any(CreateOrderDetails.class))).thenReturn(new OrderResponse(
                orderId, OrderStatus.PENDING, new BigDecimal("20.00"), Currency.ARS));

        mockMvc.perform(post("/orders/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(item(PRODUCT_ID, 2, "10.00", "ARS"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.total").value(20.00))
                .andExpect(jsonPath("$.currency").value("ARS"));
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void rejectsInvalidRequestWithoutCallingTheService(String requestBody) throws Exception {
        mockMvc.perform(post("/orders/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));

        verifyNoInteractions(orderService);
    }

    @Test
    void rejectsBusinessRuleViolation() throws Exception {
        when(orderService.create(any(CreateOrderDetails.class)))
                .thenThrow(new InvalidOrderException("mixed currencies"));

        mockMvc.perform(post("/orders/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(item(PRODUCT_ID, 1, "10.00", "ARS"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_order"));
    }

    private static Stream<String> invalidBodies() {
        return Stream.of(
                "{\"items\":[" + item(PRODUCT_ID, 1, "10.00", "ARS") + "]}",
                body(""),
                body("{\"quantity\":1,\"unitPrice\":10.00,\"currency\":\"ARS\"}"),
                body(item(PRODUCT_ID, 0, "10.00", "ARS")),
                body(item(PRODUCT_ID, 1, "-1.00", "ARS")),
                body(item(PRODUCT_ID, 1, "10.00", "EUR")),
                body("{\"productId\":\"" + PRODUCT_ID
                        + "\",\"quantity\":1,\"unitPrice\":10.00}"));
    }

    private static String body(String items) {
        return "{\"tenantId\":\"" + TENANT_ID + "\",\"items\":[" + items + "]}";
    }

    private static String item(String productId, int quantity, String unitPrice, String currency) {
        return String.format(
                "{\"productId\":\"%s\",\"quantity\":%d,\"unitPrice\":%s,\"currency\":\"%s\"}",
                productId, quantity, unitPrice, currency);
    }
}
