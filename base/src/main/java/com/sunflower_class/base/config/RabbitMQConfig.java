package com.sunflower_class.base.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Configuration
public class RabbitMQConfig {

    public static final String DIRECT_EXCHANGE = "direct.exchange";

    public static final String COURSE_CACHE_QUEUE = "course.cache.queue";
    public static final String COURSE_SEARCH_QUEUE = "course.search.queue";
    public static final String COURSE_ORDER_QUEUE = "course.order.queue";

    public static final String COURSE_CACHE_ROUTING_KEY = "course.cache";
    public static final String COURSE_SEARCH_ROUTING_KEY = "course.search";
    public static final String COURSE_ORDER_ROUTING_KEY = "course.order";

    @Bean
    @Primary
    public MessageConverter messageConverter(JsonMapper objectMapper) {

        return new JacksonJsonMessageConverter(
                objectMapper,
                "com.sunflower_class.**");
    }

    @Bean
    @Primary
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);

        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("消息未到达交换机: correlationId={}, cause={}",
                        correlationData != null ? correlationData.getId() : null, cause);
            }
        });

        rabbitTemplate.setReturnsCallback(returned -> {
            log.error("消息未路由到队列: exchange={}, routingKey={}, replyCode={}, replyText={}",
                    returned.getExchange(), returned.getRoutingKey(),
                    returned.getReplyCode(), returned.getReplyText());
        });

        return rabbitTemplate;
    }

    @Bean
    @Primary
    public RabbitListenerContainerFactory<?> rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(1);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(3);
        return factory;
    }

    @Bean
    public DirectExchange directExchange() {
        return new DirectExchange(DIRECT_EXCHANGE, true, false);
    }

    @Bean
    public Queue courseCacheQueue() {
        return QueueBuilder.durable(COURSE_CACHE_QUEUE).build();
    }

    @Bean
    public Queue courseSearchQueue() {
        return QueueBuilder.durable(COURSE_SEARCH_QUEUE).build();
    }

    @Bean
    public Queue courseOrderQueue() {
        return QueueBuilder.durable(COURSE_ORDER_QUEUE).build();
    }

    @Bean
    public Queue videoQueue() {
        return QueueBuilder.durable("video.queue").build();
    }

    @Bean
    public Binding courseCacheBinding() {
        return BindingBuilder
                .bind(courseCacheQueue())
                .to(directExchange())
                .with(COURSE_CACHE_ROUTING_KEY);
    }

    @Bean
    public Binding courseSearchBinding() {
        return BindingBuilder
                .bind(courseSearchQueue())
                .to(directExchange())
                .with(COURSE_SEARCH_ROUTING_KEY);
    }

    @Bean
    public Binding courseOrderBinding() {
        return BindingBuilder
                .bind(courseOrderQueue())
                .to(directExchange())
                .with(COURSE_ORDER_ROUTING_KEY);
    }

    @Bean
    public Binding videoBinding() {
        return BindingBuilder.bind(videoQueue()).to(directExchange()).with("video");
    }
}
