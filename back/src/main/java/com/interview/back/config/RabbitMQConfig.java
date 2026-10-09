package com.interview.back.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String RESUME_EXCHANGE = "resume.exchange";
    public static final String RESUME_QUEUE = "resume.parse.queue";
    public static final String RESUME_ROUTING_KEY = "resume.parse";
    public static final String RESUME_RETRY_EXCHANGE = "resume.retry.exchange";
    public static final String RESUME_RETRY_QUEUE_1 = "resume.parse.retry.1.queue";
    public static final String RESUME_RETRY_QUEUE_2 = "resume.parse.retry.2.queue";
    public static final String RESUME_RETRY_ROUTING_KEY_1 = "resume.retry.1";
    public static final String RESUME_RETRY_ROUTING_KEY_2 = "resume.retry.2";
    public static final String RESUME_DLX = "resume.dlx";
    public static final String RESUME_DLQ = "resume.parse.dlq";
    public static final String RESUME_DLQ_ROUTING_KEY = "resume.parse.dead";

    @Bean
    public DirectExchange resumeExchange() {
        return ExchangeBuilder.directExchange(RESUME_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue resumeQueue() {
        return QueueBuilder.durable(RESUME_QUEUE)
                .deadLetterExchange(RESUME_DLX)
                .deadLetterRoutingKey(RESUME_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding resumeBinding(Queue resumeQueue, DirectExchange resumeExchange) {
        return BindingBuilder.bind(resumeQueue).to(resumeExchange).with(RESUME_ROUTING_KEY);
    }

    @Bean
    public DirectExchange resumeRetryExchange() {
        return ExchangeBuilder.directExchange(RESUME_RETRY_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue resumeRetryQueue1() {
        return QueueBuilder.durable(RESUME_RETRY_QUEUE_1)
                .ttl(2_000)
                .deadLetterExchange(RESUME_EXCHANGE)
                .deadLetterRoutingKey(RESUME_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue resumeRetryQueue2() {
        return QueueBuilder.durable(RESUME_RETRY_QUEUE_2)
                .ttl(5_000)
                .deadLetterExchange(RESUME_EXCHANGE)
                .deadLetterRoutingKey(RESUME_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding resumeRetryBinding1(DirectExchange resumeRetryExchange) {
        return BindingBuilder.bind(resumeRetryQueue1())
                .to(resumeRetryExchange)
                .with(RESUME_RETRY_ROUTING_KEY_1);
    }

    @Bean
    public Binding resumeRetryBinding2(DirectExchange resumeRetryExchange) {
        return BindingBuilder.bind(resumeRetryQueue2())
                .to(resumeRetryExchange)
                .with(RESUME_RETRY_ROUTING_KEY_2);
    }

    @Bean
    public DirectExchange resumeDeadLetterExchange() {
        return ExchangeBuilder.directExchange(RESUME_DLX).durable(true).build();
    }

    @Bean
    public Queue resumeDeadLetterQueue() {
        return QueueBuilder.durable(RESUME_DLQ).build();
    }

    @Bean
    public Binding resumeDeadLetterBinding() {
        return BindingBuilder.bind(resumeDeadLetterQueue())
                .to(resumeDeadLetterExchange())
                .with(RESUME_DLQ_ROUTING_KEY);
    }

    @Bean
    public MessageConverter rabbitMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
