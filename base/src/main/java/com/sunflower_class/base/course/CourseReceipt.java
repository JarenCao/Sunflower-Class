package com.sunflower_class.base.course;

/** 消费回执只确认指定事件和服务，不能把发送成功误认为业务消费成功。 */
public record CourseReceipt(long eventId, String target, String error) {}
