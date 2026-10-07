package com.sunflower_class.api;

import com.rabbitmq.client.Channel;
import com.sunflower_class.service.orders.service.OrderService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 超时处理、可靠通知与学习回执均复用已有消息确认机制。 */
@Component
public class PaymentMaintenance {

    @Autowired
    private OrderService service;

    @Autowired
    private JsonMapper json;

    @Scheduled(fixedDelay = 5000)
    public void maintain() {
        service.maintain();
    }

    @RabbitListener(queues = "payment.receipt.queue")
    public void receipt(Message message, Channel channel) throws Exception {
        try {
            service.receipt(json.readValue(message.getBody(), String.class));
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception error) {
            channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
            throw error;
        }
    }
}
