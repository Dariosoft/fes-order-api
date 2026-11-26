package com.friendlyeshop.order.messaging.rabbit.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.friendlyeshop.order.constants.MessagingNames;
import com.friendlyeshop.order.messaging.rabbit.RetryAttempts;
import com.friendlyeshop.order.messaging.rabbit.codec.StockResultMessageCodec;
import com.friendlyeshop.order.messaging.rabbit.model.StockResultMessage;
import com.friendlyeshop.order.messaging.rabbit.publisher.StockResultDeadLetterPublisher;
import com.friendlyeshop.order.service.StockResultOutcome;
import com.friendlyeshop.order.service.StockResultService;
import com.rabbitmq.client.Channel;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.json.JsonMapper;

class StockResultListenerTest {

    private final StockResultService stockResultService = mock(StockResultService.class);

    private final StockResultDeadLetterPublisher deadLetterPublisher =
            mock(StockResultDeadLetterPublisher.class);

    private final Channel channel = mock(Channel.class);

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private StockResultListener listener;

    @BeforeEach
    void setUp() {
        listener = new StockResultListener(
                stockResultService,
                new StockResultMessageCodec(jsonMapper),
                deadLetterPublisher,
                new RetryAttempts());
    }

    @Test
    void acknowledgesAfterSuccessfulProcessing() throws Exception {
        when(stockResultService.apply(any())).thenReturn(StockResultOutcome.APPLIED);

        listener.onStockResult(message(1), channel);

        verify(channel).basicAck(1L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        verifyNoInteractions(deadLetterPublisher);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void nacksWithoutRequeueWhileAttemptsRemain(int attempt) throws Exception {
        when(stockResultService.apply(any())).thenThrow(new IllegalStateException("db down"));

        listener.onStockResult(message(attempt), channel);

        verify(channel).basicNack(1L, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verifyNoInteractions(deadLetterPublisher);
    }

    @Test
    void deadLettersAndAcknowledgesOnFifthAttempt() throws Exception {
        when(stockResultService.apply(any())).thenThrow(new IllegalStateException("db down"));
        Message message = message(5);

        listener.onStockResult(message, channel);

        verify(deadLetterPublisher).publish(message);
        verify(channel).basicAck(1L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void invalidPayloadFollowsTheRetryContract() throws Exception {
        Message message = messageWithBody("not-json", 1);

        listener.onStockResult(message, channel);

        verify(channel).basicNack(1L, false, false);
        verifyNoInteractions(deadLetterPublisher);
        verifyNoInteractions(stockResultService);
    }

    private Message message(int attempt) {
        StockResultMessage payload = new StockResultMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.now(),
                null,
                null);
        return messageWithBody(jsonMapper.writeValueAsString(payload), attempt);
    }

    private Message messageWithBody(String body, int attempt) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(1L);
        properties.setReceivedRoutingKey(MessagingNames.STOCK_RESERVED_EVENT);
        if (attempt > 1) {
            List<Map<String, Object>> deaths = new ArrayList<>();
            for (int death = 1; death < attempt; death++) {
                deaths.add(Map.of("queue", MessagingNames.STOCK_RESULTS_RETRY_QUEUE));
            }
            properties.setHeader(MessageProperties.X_DEATH, deaths);
        }
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }
}
