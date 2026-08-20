package com.sunflower_class.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sunflower_class.base.model.RestResponse;
import com.sunflower_class.service.content.service.CoursePublishService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "课程发布", description = "课程发布相关接口")
@RestController
public class CoursePublishController {

    @Autowired
    private CoursePublishService coursePublishService;

    @Operation(summary = "提交课程审核", description = "将指定课程提交审核，审核通过后可发布")
    @PostMapping("/courseaudit/commit/{courseId}")
    public RestResponse commitAudit(@PathVariable("courseId") Long courseId) {
        Long companyId = 1232141425L;
        coursePublishService.commitAudit(companyId, courseId);
        return RestResponse.success();
    }

    @Operation(summary = "发布课程", description = "将审核通过的课程进行发布，发布后学员可查看学习")
    @PostMapping("/coursepublish/{courseId}")
    public RestResponse coursepublish(@PathVariable("courseId") Long courseId) {
        Long companyId = 1232141425L;
        coursePublishService.publishCourse(companyId, courseId);
        return RestResponse.success();
    }
}