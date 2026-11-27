package com.friendlyeshop.order.messaging.rabbit.publisher;

import com.friendlyeshop.order.constants.MessagingNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class StockResultDeadLetterPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(StockResultDeadLetterPublisher.class);

    private static final String DEFAULT_EXCHANGE = "";

    private final RabbitTemplate rabbitTemplate;

    public StockResultDeadLetterPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(Message original) {
        Message deadLetter = MessageBuilder.fromClonedMessage(original).build();
        rabbitTemplate.send(DEFAULT_EXCHANGE, MessagingNames.STOCK_RESULTS_DLQ, deadLetter);
        LOG.warn(
                "Moved exhausted stock result to {} messageId={}",
                MessagingNames.STOCK_RESULTS_DLQ,
                original.getMessageProperties().getMessageId());
    }
}
