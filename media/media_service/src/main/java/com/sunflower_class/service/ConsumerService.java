package com.sunflower_class.service;

import com.rabbitmq.client.Channel;
import com.sunflower_class.model.dto.TranscodeMessageDto;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;

/**
 * 视频转码消息消费契约，接收任务数据和手动确认所需的消息通道。
 */
public interface ConsumerService {
    /**
     * 消费转码消息并抢占任务，下载、转码和上传视频，再按处理结果确认消息或记录失败并重试。
     */
    public void videoHandler(
        TranscodeMessageDto msg,
        Channel channel,
        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
    );
}
