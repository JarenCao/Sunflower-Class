package com.sunflower_class.base.course;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 消费回执只确认指定事件和服务，不能把发送成功误认为业务消费成功。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseReceipt {

    /** 事件编号，用于版本控制和重复消息去重。 */
    private long eventId;

    /** 消费目标服务。 */
    private String target;

    /** 消费失败原因，成功时为空。 */
    private String error;
}
