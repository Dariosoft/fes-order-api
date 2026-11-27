package com.friendlyeshop.order.messaging.outbox;

import java.time.Duration;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OutboxBackoff {

    private static final Duration BASE_DELAY = Duration.ofSeconds(1);

    private static final Duration MAX_DELAY = Duration.ofMinutes(5);

    public static Duration delayFor(int attempts) {
        if (attempts <= 0) {
            return BASE_DELAY;
        }
        int exponent = Math.min(attempts - 1, 20);
        Duration candidate = BASE_DELAY.multipliedBy(1L << exponent);
        return candidate.compareTo(MAX_DELAY) > 0 ? MAX_DELAY : candidate;
    }
}
