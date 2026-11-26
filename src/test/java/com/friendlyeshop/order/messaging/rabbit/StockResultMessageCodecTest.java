package com.friendlyeshop.order.messaging.rabbit;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class StockResultMessageCodecTest {

    private final StockResultMessageCodec codec =
            new StockResultMessageCodec(JsonMapper.builder().build());

    @Test
    void decodesTheCatalogReservedPayloadWithoutEventType() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String payload = """
                {
                  "eventId": "%s",
                  "orderId": "%s",
                  "reservedAt": "2026-11-26T12:00:00Z"
                }
                """.formatted(eventId, orderId);

        StockResultMessage message =
                codec.decode(payload.getBytes(StandardCharsets.UTF_8));

        assertThat(message.eventId()).isEqualTo(eventId);
        assertThat(message.orderId()).isEqualTo(orderId);
        assertThat(message.reservedAt()).isEqualTo(Instant.parse("2026-11-26T12:00:00Z"));
        assertThat(message.reason()).isNull();
        assertThat(message.rejectedAt()).isNull();
    }

    @Test
    void decodesTheCatalogRejectedPayloadWithoutEventType() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String payload = """
                {
                  "eventId": "%s",
                  "orderId": "%s",
                  "reason": "OUT_OF_STOCK",
                  "rejectedAt": "2026-11-26T12:00:00Z"
                }
                """.formatted(eventId, orderId);

        StockResultMessage message =
                codec.decode(payload.getBytes(StandardCharsets.UTF_8));

        assertThat(message.eventId()).isEqualTo(eventId);
        assertThat(message.orderId()).isEqualTo(orderId);
        assertThat(message.reason()).isEqualTo("OUT_OF_STOCK");
        assertThat(message.rejectedAt()).isEqualTo(Instant.parse("2026-11-26T12:00:00Z"));
        assertThat(message.reservedAt()).isNull();
    }
}
