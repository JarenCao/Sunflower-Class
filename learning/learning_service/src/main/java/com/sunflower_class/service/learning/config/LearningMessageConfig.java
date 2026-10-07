package com.sunflower_class.service.learning.config;

import com.sunflower_class.base.course.CourseEventConsumer;
import com.sunflower_class.base.course.CourseMessageSender;
import com.sunflower_class.service.learning.service.LearningCourseService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/** 将具体业务写入方法接入已有课程事件消费流程。 */
@Configuration
public class LearningMessageConfig {

    /** 共享重试与回执能力；目录更新仍由本服务负责。 */
    @Bean
    public CourseEventConsumer courseEventConsumer(
        LearningCourseService service,
        JsonMapper json,
        CourseMessageSender sender
    ) {
        return new CourseEventConsumer("learning", service::save, json, sender);
    }
}
