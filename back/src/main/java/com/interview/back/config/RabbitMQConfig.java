package com.interview.back.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String RESUME_EXCHANGE = "resume.exchange";
    public static final String RESUME_QUEUE = "resume.parse.queue";
    public static final String RESUME_ROUTING_KEY = "resume.parse";

    @Bean
    public DirectExchange resumeExchange() {
        return new DirectExchange(RESUME_EXCHANGE);
    }

    @Bean
    public Queue resumeQueue() {
        return new Queue(RESUME_QUEUE, true);
    }

    @Bean
    public Binding resumeBinding(Queue resumeQueue, DirectExchange resumeExchange) {
        return BindingBuilder.bind(resumeQueue).to(resumeExchange).with(RESUME_ROUTING_KEY);
    }
}
