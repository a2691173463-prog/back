package com.interview.back.mq;

import com.interview.back.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class ResumeMqProducer {

    private final RabbitTemplate rabbitTemplate;

    public ResumeMqProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendParseTask(Long resumeId) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.RESUME_EXCHANGE, RabbitMQConfig.RESUME_ROUTING_KEY, resumeId);
    }
}
