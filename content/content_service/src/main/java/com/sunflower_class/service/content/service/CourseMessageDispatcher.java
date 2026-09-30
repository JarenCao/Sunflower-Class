package com.sunflower_class.service.content.service;

import com.rabbitmq.client.Channel;
import com.sunflower_class.base.config.RabbitMQConfig;
import com.sunflower_class.base.course.CourseEvent;
import com.sunflower_class.base.course.CourseMessageSender;
import com.sunflower_class.base.course.CourseReceipt;
import com.sunflower_class.service.content.mapper.MqMessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** 扫描事务消息表，可靠投递到搜索和学习服务，并汇总持久化消费回执。 */
@Slf4j
@Service
public class CourseMessageDispatcher {

    private final MqMessageMapper messages;
    private final CourseMessageSender sender;
    private final JsonMapper json;

    /** 复用消息表、全局 JSON 与确认发送器，不引入新的任务框架。 */
    public CourseMessageDispatcher(
        MqMessageMapper messages,
        CourseMessageSender sender,
        JsonMapper json
    ) {
        this.messages = messages;
        this.sender = sender;
        this.json = json;
    }

    /** 每五秒扫描；已发送未收到回执的事件同样可重投，由下游版本校验保证幂等。 */
    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void dispatch() {
        for (var message : messages.pending()) {
            if (messages.claim(message.getId()) != 1) continue;
            try {
                var event = new CourseEvent(
                    message.getId(),
                    Long.parseLong(message.getBusinessKey1()),
                    message.getBusinessKey3(),
                    message.getPayload()
                );
                if (!"1".equals(message.getStageState3())) sender.send(
                    RabbitMQConfig.COURSE_SEARCH_ROUTING_KEY,
                    event
                );
                if (!"1".equals(message.getStageState4())) sender.send(
                    RabbitMQConfig.COURSE_LEARNING_ROUTING_KEY,
                    event
                );
                messages.sent(message.getId());
            } catch (Exception error) {
                messages.failed(
                    message.getId(),
                    "课程事件投递失败：" + error.getClass().getSimpleName()
                );
                log.warn("课程事件投递失败，等待重试，eventId={}", message.getId(), error);
            }
        }
    }

    /** 数据库回执更新成功后才确认；重复回执重复设置同一阶段，不重复触发业务。 */
    @RabbitListener(queues = RabbitMQConfig.COURSE_RECEIPT_QUEUE)
    public void receipt(Message message, Channel channel) throws Exception {
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            var receipt = json.readValue(message.getBody(), CourseReceipt.class);
            if (receipt.eventId() <= 0) throw new IllegalArgumentException("事件编号无效");
            String reason =
                receipt.error() == null
                    ? null
                    : receipt.error().substring(0, Math.min(200, receipt.error().length()));
            if ("search".equals(receipt.target())) {
                if (reason == null) messages.searchDone(receipt.eventId());
                else messages.searchFailed(receipt.eventId(), reason);
            } else if ("learning".equals(receipt.target())) {
                if (reason == null) messages.learningDone(receipt.eventId());
                else messages.learningFailed(receipt.eventId(), reason);
            } else throw new IllegalArgumentException("消费服务无效");
            channel.basicAck(tag, false);
        } catch (IllegalArgumentException error) {
            log.error("拒绝格式错误的课程回执", error);
            channel.basicReject(tag, false);
        } catch (Exception error) {
            channel.basicNack(tag, false, true);
            throw error;
        }
    }
}
