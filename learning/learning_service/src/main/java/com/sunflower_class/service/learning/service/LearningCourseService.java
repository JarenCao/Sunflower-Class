package com.sunflower_class.service.learning.service;

import com.sunflower_class.base.course.CourseEvent;
import com.sunflower_class.model.dto.CourseDirectoryDto;

/** 学习服务只维护发布目录，后续学习资格与播放鉴权独立实现。 */
public interface LearningCourseService {
    /** 按事件版本事务更新目录，忽略重复和旧版本。 */
    void save(CourseEvent event);

    /** 获取已发布课程目录，未发布或已下架返回 404。 */
    CourseDirectoryDto directory(long courseId);
}
