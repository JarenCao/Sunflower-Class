package com.sunflower_class.service.learning.service.impl;

import com.sunflower_class.base.course.CourseEvent;
import com.sunflower_class.model.dto.CourseDirectoryDto;
import com.sunflower_class.service.learning.service.LearningCourseService;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/** 用独立目录表维护不可变课程快照，按课程主键及事件版本保证幂等。 */
@Service
public class LearningCourseServiceImpl implements LearningCourseService {

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    /** 复用 Spring JDBC 和现有内容库数据源，不访问内容草稿表。 */
    public LearningCourseServiceImpl(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** 事务提交后共用消费者才回传成功；低版本事件不覆盖新目录或下架状态。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(CourseEvent event) {
        var course = json.readValue(event.snapshot(), Map.class);
        jdbc.update(
            "INSERT INTO learning_course (id,event_id,status,name,tags,category,payload) VALUES (?,?,?,?,?,?,?) " +
                "ON DUPLICATE KEY UPDATE status=IF(event_id<VALUES(event_id),VALUES(status),status), " +
                "name=IF(event_id<VALUES(event_id),VALUES(name),name), tags=IF(event_id<VALUES(event_id),VALUES(tags),tags), " +
                "category=IF(event_id<VALUES(event_id),VALUES(category),category), payload=IF(event_id<VALUES(event_id),VALUES(payload),payload), " +
                "event_id=GREATEST(event_id,VALUES(event_id))",
            event.courseId(),
            event.eventId(),
            event.status(),
            Objects.toString(course.get("name"), ""),
            Objects.toString(course.get("tags"), ""),
            Objects.toString(course.get("mtName"), "课程"),
            event.snapshot()
        );
    }

    /** 目录只读取本服务已发布副本，不能把草稿目录误当成可公开目录。 */
    @Override
    public CourseDirectoryDto directory(long courseId) {
        var values = jdbc.queryForList(
            "SELECT payload FROM learning_course WHERE id=? AND status='30502'",
            String.class,
            courseId
        );
        if (values.isEmpty()) throw new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "课程未发布或不存在"
        );
        var course = json.readValue(values.getFirst(), Map.class);
        return new CourseDirectoryDto(courseId, Objects.toString(course.get("teachplan"), "[]"));
    }
}
