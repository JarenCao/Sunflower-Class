package com.sunflower_class.api;

import com.rabbitmq.client.Channel;
import com.sunflower_class.base.course.CourseMessageSender;
import com.sunflower_class.base.payment.PaymentSuccessEvent;
import com.sunflower_class.service.learning.service.LearningCourseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 学习资格提交后可靠发送回执，再确认消息；失败消息保留在延迟或失败队列。 */
@Component
@Slf4j
public class PaymentLearningConsumer {

    @Autowired
    private LearningCourseService service;

    @Autowired
    private CourseMessageSender sender;

    @Autowired
    private JsonMapper json;

    @RabbitListener(queues = "payment.learning.queue")
    public void consume(Message message, Channel channel) throws Exception {
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            PaymentSuccessEvent event = json.readValue(
                message.getBody(),
                PaymentSuccessEvent.class
            );
            service.payment(event);
            sender.send("payment.receipt", event.getOrderId());
            channel.basicAck(tag, false);
        } catch (Exception error) {
            log.warn(
                "支付资格处理失败，messageId={}，type={}",
                message.getMessageProperties().getMessageId(),
                error.getClass().getSimpleName()
            );
            Number previous = message.getMessageProperties().getHeader("payment-attempt");
            int attempts = previous == null ? 1 : previous.intValue() + 1;
            Message retry = MessageBuilder.fromClonedMessage(message)
                .setHeader("payment-attempt", attempts)
                .build();
            try {
                sender.send(
                    attempts < 10 ? "payment.learning.retry" : "payment.learning.failed",
                    retry
                );
                channel.basicAck(tag, false);
            } catch (Exception failed) {
                channel.basicNack(tag, false, true);
                throw failed;
            }
        }
    }
}
