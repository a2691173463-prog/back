package com.interview.back.mq;

import com.interview.back.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ResumeMqProducer {

    private final RabbitTemplate rabbitTemplate;

    @Value("${mq.resume.publisher-max-attempts:3}")
    private int publisherMaxAttempts;

    @Value("${mq.resume.confirm-timeout-seconds:5}")
    private int confirmTimeoutSeconds;

    public ResumeMqProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitTemplate.setMandatory(true);
        this.rabbitTemplate.setReturnsCallback(returned -> log.error(
                "RabbitMQ returned message, exchange={}, routingKey={}, replyCode={}, replyText={}",
                returned.getExchange(),
                returned.getRoutingKey(),
                returned.getReplyCode(),
                returned.getReplyText()
        ));
    }

    public void sendParseTask(ResumeParseMessage task) {
        sendWithRetry(
                RabbitMQConfig.RESUME_EXCHANGE,
                RabbitMQConfig.RESUME_ROUTING_KEY,
                task
        );
    }

    public void sendRetryTask(ResumeParseMessage task) {
        String routingKey = task.attempt() <= 1
                ? RabbitMQConfig.RESUME_RETRY_ROUTING_KEY_1
                : RabbitMQConfig.RESUME_RETRY_ROUTING_KEY_2;
        sendWithRetry(RabbitMQConfig.RESUME_RETRY_EXCHANGE, routingKey, task);
    }

    private void sendWithRetry(String exchange, String routingKey, ResumeParseMessage task) {
        RuntimeException lastError = null;
        int maxAttempts = Math.max(1, publisherMaxAttempts);

        for (int publishAttempt = 1; publishAttempt <= maxAttempts; publishAttempt++) {
            try {
                sendAndConfirm(exchange, routingKey, task);
                return;
            } catch (Exception e) {
                lastError = new IllegalStateException(
                        "Failed to publish RabbitMQ message " + task.messageId()
                                + " on attempt " + publishAttempt,
                        e
                );
                log.warn(
                        "RabbitMQ publish failed, messageId={}, resumeId={}, publishAttempt={}/{}",
                        task.messageId(),
                        task.resumeId(),
                        publishAttempt,
                        maxAttempts,
                        e
                );
                if (publishAttempt < maxAttempts) {
                    sleepBeforeRetry(publishAttempt);
                }
            }
        }
        throw lastError;
    }

    private void sendAndConfirm(
            String exchange,
            String routingKey,
            ResumeParseMessage task
    ) throws Exception {
        CorrelationData correlationData = new CorrelationData(task.messageId());
        rabbitTemplate.convertAndSend(
                exchange,
                routingKey,
                task,
                message -> {
                    message.getMessageProperties().setMessageId(task.messageId());
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    message.getMessageProperties().setHeader("resumeId", task.resumeId());
                    message.getMessageProperties().setHeader("retryAttempt", task.attempt());
                    return message;
                },
                correlationData
        );

        CorrelationData.Confirm confirm = correlationData.getFuture()
                .get(confirmTimeoutSeconds, TimeUnit.SECONDS);
        if (!confirm.isAck()) {
            throw new IllegalStateException("Broker NACK: " + confirm.getReason());
        }
        if (correlationData.getReturned() != null) {
            throw new IllegalStateException(
                    "Message was not routed: " + correlationData.getReturned().getReplyText()
            );
        }
        log.info(
                "RabbitMQ publish confirmed, messageId={}, resumeId={}, exchange={}, routingKey={}",
                task.messageId(),
                task.resumeId(),
                exchange,
                routingKey
        );
    }

    private void sleepBeforeRetry(int publishAttempt) {
        try {
            Thread.sleep(Math.min(2_000L, 250L * (1L << (publishAttempt - 1))));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("RabbitMQ publish retry interrupted", e);
        }
    }
}
