package com.sunflower_class.api;

import com.sunflower_class.model.dto.CourseDirectoryDto;
import com.sunflower_class.service.learning.service.LearningCourseService;
import org.springframework.web.bind.annotation.*;

/** 提供公开课程目录，不在接口层代替学习资格或播放权限校验。 */
@RestController
public class LearningCourseController {

    private final LearningCourseService service;

    /** 注入学习目录业务，接口层不直接操作数据库。 */
    public LearningCourseController(LearningCourseService service) {
        this.service = service;
    }

    /** 通过学习服务的发布副本返回实际目录。 */
    @GetMapping("/courses/{id}/directory")
    public CourseDirectoryDto directory(@PathVariable long id) {
        return service.directory(id);
    }
}
