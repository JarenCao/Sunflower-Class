package com.sunflower_class.service.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunflower_class.model.po.MqMessage;

/** 复用 MyBatis-Plus 的写入能力，将课程发布消息保存到内容库现有的 mq_message 表。 */
public interface MqMessageMapper extends BaseMapper<MqMessage> {}
