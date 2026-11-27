package com.friendlyeshop.order.messaging.rabbit.codec;

import com.friendlyeshop.order.messaging.rabbit.model.OrderPlacedMessage;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class OrderPlacedMessageCodec {

    private final JsonMapper jsonMapper;

    public OrderPlacedMessageCodec(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public String encode(OrderPlacedMessage message) {
        return jsonMapper.writeValueAsString(message);
    }

    public OrderPlacedMessage decode(String payload) {
        return jsonMapper.readValue(payload, OrderPlacedMessage.class);
    }
}
