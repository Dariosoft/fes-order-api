package com.friendlyeshop.order.messaging.rabbit;

import com.friendlyeshop.order.constants.MessagingNames;
import java.util.List;
import java.util.Map;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.stereotype.Component;

@Component
public class RetryAttempts {

    public static final int MAX_ATTEMPTS = 5;

    private static final String X_DEATH_QUEUE = "queue";

    public int currentAttempt(Message message) {
        Object xDeath = message.getMessageProperties().getHeaders().get(MessageProperties.X_DEATH);
        if (!(xDeath instanceof List<?> deaths)) {
            return 1;
        }
        long retryDeaths = deaths.stream()
                .filter(Map.class::isInstance)
                .map(death -> (Map<?, ?>) death)
                .filter(death -> MessagingNames.STOCK_RESULTS_RETRY_QUEUE.equals(death.get(X_DEATH_QUEUE)))
                .mapToLong(RetryAttempts::deathCount)
                .sum();
        return (int) Math.min(retryDeaths + 1, Integer.MAX_VALUE);
    }

    private static long deathCount(Map<?, ?> death) {
        Object count = death.get("count");
        return count instanceof Number number ? number.longValue() : 1L;
    }
}
