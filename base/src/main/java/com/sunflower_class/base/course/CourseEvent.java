package com.sunflower_class.base.course;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 发布事件携带正式发布时保存的快照；事件编号同时作为下游更新版本。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseEvent {

    /** 事件编号，用于版本控制和重复消息去重。 */
    private long eventId;

    /** 课程编号。 */
    private long courseId;

    /** 业务状态码，沿用数据库字典定义。 */
    private String status;

    /** 原业务快照，字段结构与消息协议保持一致。 */
    private String snapshot;
}
