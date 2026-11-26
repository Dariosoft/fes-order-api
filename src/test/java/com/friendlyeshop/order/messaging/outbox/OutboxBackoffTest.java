package com.friendlyeshop.order.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class OutboxBackoffTest {

    @Test
    void growsExponentiallyAndStopsAtTheCap() {
        assertThat(OutboxBackoff.delayFor(0)).isEqualTo(Duration.ofSeconds(1));
        assertThat(OutboxBackoff.delayFor(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(OutboxBackoff.delayFor(2)).isEqualTo(Duration.ofSeconds(2));
        assertThat(OutboxBackoff.delayFor(3)).isEqualTo(Duration.ofSeconds(4));
        assertThat(OutboxBackoff.delayFor(30)).isEqualTo(Duration.ofMinutes(5));
    }
}
