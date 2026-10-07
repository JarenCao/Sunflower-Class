package com.sunflower_class.base.course;

import com.rabbitmq.client.Channel;
import com.sunflower_class.base.config.RabbitMQConfig;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import tools.jackson.databind.json.JsonMapper;

/** 两种存储后端复用同一消息协议、延迟重试和回执，业务写入由各自服务负责。 */
@Slf4j
public class CourseEventConsumer {

    private final String target;
    private final Consumer<CourseEvent> writer;
    private final JsonMapper json;
    private final CourseMessageSender sender;

    /** 复用标准 Consumer 接入具体写入方法，不另建持久化抽象层。 */
    public CourseEventConsumer(
        String target,
        Consumer<CourseEvent> writer,
        JsonMapper json,
        CourseMessageSender sender
    ) {
        if (!List.of("search", "learning").contains(target)) throw new IllegalArgumentException(
            "课程消费服务无效"
        );
        this.target = target;
        this.writer = writer;
        this.json = json;
        this.sender = sender;
    }

    /** 消费成功须先提交副本，再可靠发送回执，最后确认原消息。 */
    public void consume(Message message, Channel channel) throws Exception {
        long tag = message.getMessageProperties().getDeliveryTag();
        CourseEvent event = null;
        try {
            event = json.readValue(message.getBody(), CourseEvent.class);
            Map course = json.readValue(event.getSnapshot(), Map.class);
            if (
                event.getEventId() <= 0 ||
                event.getCourseId() <= 0 ||
                !(course.get("id") instanceof Number id) ||
                id.longValue() != event.getCourseId() ||
                !List.of("30502", "30503").contains(event.getStatus()) ||
                !event.getStatus().equals(course.get("status"))
            ) {
                throw new IllegalArgumentException("课程事件与快照不一致");
            }
            writer.accept(event);
            sender.send(
                RabbitMQConfig.COURSE_RECEIPT_ROUTING_KEY,
                new CourseReceipt(event.getEventId(), target, null)
            );
            channel.basicAck(tag, false);
        } catch (Exception error) {
            log.warn(
                "课程副本处理失败，service={}，messageId={}",
                target,
                message.getMessageProperties().getMessageId(),
                error
            );
            // 失败消息延迟十秒重试，十次后保留在失败队列，避免无限快速重新入队。
            Number previous = message.getMessageProperties().getHeader("course-attempt");
            int attempts = previous == null ? 1 : previous.intValue() + 1;
            Message retry = MessageBuilder.fromClonedMessage(message)
                .setHeader("course-attempt", attempts)
                .build();
            try {
                // 业务失败也回传持久化状态；错误只含类型，不向管理端泄漏连接凭据。
                if (event != null && event.getEventId() > 0) {
                    sender.send(
                        RabbitMQConfig.COURSE_RECEIPT_ROUTING_KEY,
                        new CourseReceipt(
                            event.getEventId(),
                            target,
                            "课程副本处理失败：" + error.getClass().getSimpleName()
                        )
                    );
                }
                sender.send("course." + target + (attempts < 10 ? ".retry" : ".failed"), retry);
                channel.basicAck(tag, false);
            } catch (Exception retryError) {
                // 重试队列不可达时保留原消息，不能静默丢失课程同步事件。
                channel.basicNack(tag, false, true);
                throw retryError;
            }
        }
    }
}
