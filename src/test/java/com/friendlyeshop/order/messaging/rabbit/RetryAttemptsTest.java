package com.friendlyeshop.order.messaging.rabbit;

import static org.assertj.core.api.Assertions.assertThat;

import com.friendlyeshop.order.constants.MessagingNames;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class RetryAttemptsTest {

    private final RetryAttempts retryAttempts = new RetryAttempts();

    @Test
    void firstDeliveryCountsAsOneAttempt() {
        assertThat(retryAttempts.currentAttempt(messageWithoutHeader())).isEqualTo(1);
    }

    @Test
    void sumsCountsFromTheRetryQueue() {
        Message message = messageWithXDeath(List.of(
                Map.of("queue", MessagingNames.STOCK_RESULTS_RETRY_QUEUE, "count", 2L),
                Map.of("queue", MessagingNames.STOCK_RESULTS_RETRY_QUEUE, "count", 1L)));

        assertThat(retryAttempts.currentAttempt(message)).isEqualTo(4);
    }

    @Test
    void ignoresDeathsFromOtherQueues() {
        Message message = messageWithXDeath(List.of(
                Map.of("queue", MessagingNames.STOCK_RESULTS_QUEUE, "count", 5L),
                Map.of("queue", MessagingNames.STOCK_RESULTS_RETRY_QUEUE, "count", 1L)));

        assertThat(retryAttempts.currentAttempt(message)).isEqualTo(2);
    }

    @Test
    void treatsMissingCountAsOneDeath() {
        Message message = messageWithXDeath(
                List.of(Map.of("queue", MessagingNames.STOCK_RESULTS_RETRY_QUEUE)));

        assertThat(retryAttempts.currentAttempt(message)).isEqualTo(2);
    }

    private static Message messageWithoutHeader() {
        return new Message("{}".getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }

    private static Message messageWithXDeath(List<Map<String, ?>> xDeath) {
        MessageProperties properties = new MessageProperties();
        properties.setHeader(MessageProperties.X_DEATH, xDeath);
        return new Message("{}".getBytes(StandardCharsets.UTF_8), properties);
    }
}
