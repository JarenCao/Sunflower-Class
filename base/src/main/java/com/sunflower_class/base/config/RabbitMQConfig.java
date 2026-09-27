package com.sunflower_class.base.config;

import lombok.extern.slf4j.Slf4j;
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
import tools.jackson.databind.json.JsonMapper;

/**
 * 配置消息转换、发送确认、消费容器及课程与视频消息的队列绑定。
 */
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

    /**
     * 创建 RabbitMQ 的 JSON 消息转换器，允许反序列化项目 DTO 包中的消息类型。
     */
    @Bean
    @Primary
    public MessageConverter messageConverter(JsonMapper objectMapper) {
        return new JacksonJsonMessageConverter(objectMapper, "com.sunflower_class.model.dto");
    }

    /**
     * 创建消息发送模板，并注册交换机确认与未路由消息的日志回调；回调本身不重发消息。
     */
    @Bean
    @Primary
    public RabbitTemplate rabbitTemplate(
        ConnectionFactory connectionFactory,
        MessageConverter messageConverter
    ) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);

        // 确认回调仅在交换机未确认时记录原因，不代表队列消费结果。
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error(
                    "消息未到达交换机: correlationId={}, cause={}",
                    correlationData != null ? correlationData.getId() : null,
                    cause
                );
            }
        });

        // 退回回调记录无法路由到队列的交换机和路由键信息。
        rabbitTemplate.setReturnsCallback(returned -> {
            log.error(
                "消息未路由到队列: exchange={}, routingKey={}, replyCode={}, replyText={}",
                returned.getExchange(),
                returned.getRoutingKey(),
                returned.getReplyCode(),
                returned.getReplyText()
            );
        });

        return rabbitTemplate;
    }

    /**
     * 创建手动确认的消费容器；每个消费者预取一条消息，并发消费者数量为 1 到 3。
     */
    @Bean
    @Primary
    public RabbitListenerContainerFactory<?> rabbitListenerContainerFactory(
        ConnectionFactory connectionFactory,
        MessageConverter messageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(1);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(3);
        return factory;
    }

    /**
     * 声明持久化直连交换机，按路由键分发业务消息。
     */
    @Bean
    public DirectExchange directExchange() {
        return new DirectExchange(DIRECT_EXCHANGE, true, false);
    }

    /**
     * 声明课程缓存消息的持久化队列；队列声明不代表缓存消费功能已实现。
     */
    @Bean
    public Queue courseCacheQueue() {
        return QueueBuilder.durable(COURSE_CACHE_QUEUE).build();
    }

    /**
     * 声明课程搜索同步的持久化队列；搜索消费者需另行实现。
     */
    @Bean
    public Queue courseSearchQueue() {
        return QueueBuilder.durable(COURSE_SEARCH_QUEUE).build();
    }

    /**
     * 声明课程订单消息的持久化队列；订单业务需另行接入。
     */
    @Bean
    public Queue courseOrderQueue() {
        return QueueBuilder.durable(COURSE_ORDER_QUEUE).build();
    }

    /**
     * 声明视频转码持久化队列，供转码消费者接收任务。
     */
    @Bean
    public Queue videoQueue() {
        return QueueBuilder.durable("video.queue").build();
    }

    /**
     * 将缓存队列绑定到直连交换机的课程缓存路由键。
     */
    @Bean
    public Binding courseCacheBinding() {
        return BindingBuilder.bind(courseCacheQueue())
            .to(directExchange())
            .with(COURSE_CACHE_ROUTING_KEY);
    }

    /**
     * 将搜索队列绑定到直连交换机的课程搜索路由键。
     */
    @Bean
    public Binding courseSearchBinding() {
        return BindingBuilder.bind(courseSearchQueue())
            .to(directExchange())
            .with(COURSE_SEARCH_ROUTING_KEY);
    }

    /**
     * 将订单队列绑定到直连交换机的课程订单路由键。
     */
    @Bean
    public Binding courseOrderBinding() {
        return BindingBuilder.bind(courseOrderQueue())
            .to(directExchange())
            .with(COURSE_ORDER_ROUTING_KEY);
    }

    /**
     * 将视频转码队列绑定到 video 路由键，接收重试调度发送的任务。
     */
    @Bean
    public Binding videoBinding() {
        return BindingBuilder.bind(videoQueue()).to(directExchange()).with("video");
    }
}
