package com.sunflower_class.base.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public Queue transcodeQueue() {
        return new Queue("transcode.queue", true);
    }

    @Bean
    public Exchange transcodeExchange() {
        return new DirectExchange("transcode.exchange", true, false);
    }

    @Bean
    public Binding transcodeBinding() {
        return BindingBuilder
                .bind(transcodeQueue())
                .to(transcodeExchange())
                .with("transcode")
                .noargs();
    }
}