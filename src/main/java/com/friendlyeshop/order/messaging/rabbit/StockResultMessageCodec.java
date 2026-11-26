package com.friendlyeshop.order.messaging.rabbit;

import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class StockResultMessageCodec {

    private final JsonMapper jsonMapper;

    public StockResultMessageCodec(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public StockResultMessage decode(byte[] payload) {
        return jsonMapper.readValue(payload, StockResultMessage.class);
    }
}
