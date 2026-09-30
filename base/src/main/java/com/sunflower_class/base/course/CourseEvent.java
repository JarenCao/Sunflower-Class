package com.sunflower_class.base.course;

/** 发布事件携带不可变的正式快照；事件编号同时作为下游更新版本。 */
public record CourseEvent(long eventId, long courseId, String status, String snapshot) {}
