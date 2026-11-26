package com.friendlyeshop.order.messaging.rabbit.listener;

import com.friendlyeshop.order.messaging.rabbit.RetryAttempts;
import com.friendlyeshop.order.messaging.rabbit.codec.StockResultMessageCodec;
import com.friendlyeshop.order.messaging.rabbit.model.StockResultMessage;
import com.friendlyeshop.order.messaging.rabbit.publisher.StockResultDeadLetterPublisher;
import com.friendlyeshop.order.model.dto.in.StockResultDetails;
import com.friendlyeshop.order.service.StockResultService;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class StockResultListener {

    private static final Logger LOG = LoggerFactory.getLogger(StockResultListener.class);

    private final StockResultService stockResultService;

    private final StockResultMessageCodec messageCodec;

    private final StockResultDeadLetterPublisher deadLetterPublisher;

    private final RetryAttempts retryAttempts;

    public StockResultListener(
            StockResultService stockResultService,
            StockResultMessageCodec messageCodec,
            StockResultDeadLetterPublisher deadLetterPublisher,
            RetryAttempts retryAttempts) {
        this.stockResultService = stockResultService;
        this.messageCodec = messageCodec;
        this.deadLetterPublisher = deadLetterPublisher;
        this.retryAttempts = retryAttempts;
    }

    @RabbitListener(queues = "${app.rabbitmq.stock-results-queue:order.stock-results}")
    public void onStockResult(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            StockResultMessage payload = messageCodec.decode(message.getBody());
            String eventType = message.getMessageProperties().getReceivedRoutingKey();
            stockResultService.apply(StockResultDetails.from(payload, eventType));
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException exception) {
            handleFailure(message, channel, deliveryTag, exception);
        }
    }

    private void handleFailure(
            Message message, Channel channel, long deliveryTag, RuntimeException exception)
            throws IOException {
        int attempt = retryAttempts.currentAttempt(message);
        if (attempt < RetryAttempts.MAX_ATTEMPTS) {
            LOG.warn(
                    "Stock result processing failed on attempt {} of {}; retrying without requeue",
                    attempt, RetryAttempts.MAX_ATTEMPTS, exception);
            channel.basicNack(deliveryTag, false, false);
            return;
        }
        LOG.error(
                "Stock result processing exhausted {} attempts; moving message to the dead letter queue",
                RetryAttempts.MAX_ATTEMPTS, exception);
        deadLetterPublisher.publish(message);
        channel.basicAck(deliveryTag, false);
    }
}
