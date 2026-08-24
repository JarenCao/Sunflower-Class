package com.sunflower_class.service;

import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;

import com.rabbitmq.client.Channel;
import com.sunflower_class.model.dto.TranscodeMessageDto;

public interface ConsumerService {
    public void videoHandler(TranscodeMessageDto msg,
                             Channel channel,
                             @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag);
}
