package com.sunflower_class.service.search.config;

import com.sunflower_class.base.course.CourseEventConsumer;
import com.sunflower_class.base.course.CourseMessageSender;
import com.sunflower_class.service.search.service.CourseSearchService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/** 将具体业务写入方法接入已有课程事件消费流程。 */
@Configuration
public class SearchMessageConfig {

    /** 共享重试与回执能力；索引更新仍由本服务负责。 */
    @Bean
    public CourseEventConsumer courseEventConsumer(
        CourseSearchService service,
        JsonMapper json,
        CourseMessageSender sender
    ) {
        return new CourseEventConsumer("search", service::save, json, sender);
    }
}
