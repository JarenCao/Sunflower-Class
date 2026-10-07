package com.sunflower_class.service.search.service;

import com.rabbitmq.client.Channel;
import com.sunflower_class.base.config.RabbitMQConfig;
import com.sunflower_class.base.course.CourseEventConsumer;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 监听课程发布与下架事件，不在 REST 控制器中处理消息业务。 */
@Service
public class SearchMessageConsumer {

    @Autowired
    private CourseEventConsumer consumer;

    /** 注入共用消费流程与本服务的业务写入能力。 */

    /** 消费成功并发送回执后才确认队列消息。 */
    @RabbitListener(queues = RabbitMQConfig.COURSE_SEARCH_QUEUE)
    public void consume(Message message, Channel channel) throws Exception {
        consumer.consume(message, channel);
    }
}
