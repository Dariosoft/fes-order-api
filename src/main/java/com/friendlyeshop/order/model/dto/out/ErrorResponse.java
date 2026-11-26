package com.friendlyeshop.order.model.dto.out;

import java.util.List;

public record ErrorResponse(String code, String message, List<String> fields) {

    public static ErrorResponse validation(List<String> fields) {
        return new ErrorResponse("validation_error", "Solicitud inválida", List.copyOf(fields));
    }

    public static ErrorResponse business(String message) {
        return new ErrorResponse("invalid_order", message, List.of());
    }
}
