package com.sunflower_class.base.course;

import com.sunflower_class.base.config.RabbitMQConfig;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** RabbitMQ 发布确认和退回机制，供课程事件与消费回执可靠发送。 */
@Component
public class CourseMessageSender {

    @Autowired
    private RabbitTemplate template;

    /** 注入已有消息模板，保留全局转换器及连接配置。 */

    /** 只有交换机确认且没有退回才算成功；超时由调用方持久化重试或重新入队。 */
    public void send(String routingKey, Object payload) throws Exception {
        CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
        template.convertAndSend(RabbitMQConfig.DIRECT_EXCHANGE, routingKey, payload, correlation);
        CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
        if (!confirm.ack() || correlation.getReturned() != null) {
            throw new IllegalStateException("课程消息未被队列可靠接收");
        }
    }
}
