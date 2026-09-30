package com.sunflower_class.api;

import static com.sunflower_class.base.model.BusinessCodes.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunflower_class.base.exception.GlobalException;
import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.base.model.RestResponse;
import com.sunflower_class.model.po.CoursePublish;
import com.sunflower_class.service.content.mapper.CoursePublishMapper;
import com.sunflower_class.service.content.service.CoursePublishService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 课程提交审核、发布及下架入口，同时向学员端提供已发布快照的只读查询。
 */
@Tag(name = "课程发布", description = "课程发布相关接口")
@RestController
public class CoursePublishController {

    @Value("${sunflower.company-id}")
    private Long companyId;

    // 开发阶段从服务配置标识审核人；正式身份与角色校验将在认证功能中接入。
    @Value("${sunflower.reviewer-name:本地审核员}")
    private String reviewerName;

    @Autowired
    private CoursePublishService coursePublishService;

    @Autowired
    private CoursePublishMapper coursePublishMapper;

    /** 学员只读取已发布快照，管理端草稿修改不会直接替换这里的数据。 */
    @GetMapping("/published-courses")
    public PageResult<CoursePublish> publishedCourses(
        @RequestParam(defaultValue = "1") long pageNo,
        @RequestParam(defaultValue = "20") long pageSize,
        @RequestParam(defaultValue = "") String q
    ) {
        var query = new LambdaQueryWrapper<CoursePublish>()
            .eq(CoursePublish::getStatus, COURSE_PUBLISHED)
            .like(!q.isBlank(), CoursePublish::getName, q)
            .orderByDesc(CoursePublish::getOnlineDate);
        var page = coursePublishMapper.selectPage(
            new Page<>(Math.max(1, pageNo), Math.min(100, Math.max(1, pageSize))),
            query
        );
        return new PageResult<>(
            page.getRecords(),
            page.getTotal(),
            page.getCurrent(),
            page.getSize()
        );
    }

    /**
     * 读取指定编号的已发布课程快照，未发布或不存在的课程不返回详情。
     */
    @GetMapping("/published-courses/{id}")
    public CoursePublish publishedCourse(@PathVariable Long id) {
        var course = coursePublishMapper.selectById(id);
        if (course == null || !COURSE_PUBLISHED.equals(course.getStatus())) GlobalException.cast(
            "课程未发布或不存在"
        );
        return course;
    }

    /**
     * 校验机构归属、当前审核状态、教学计划及营销信息，生成待审核快照并更新审核状态。
     */
    @Operation(summary = "提交课程审核", description = "将指定课程提交审核，审核通过后可发布")
    @PostMapping("/courseaudit/commit/{courseId}")
    public RestResponse commitAudit(@PathVariable("courseId") Long courseId) {
        coursePublishService.commitAudit(companyId, courseId);
        return RestResponse.success();
    }

    /** 请求体只提供审核结论与意见；审核人由服务配置决定，避免由页面伪造。 */
    public record ReviewDecision(Boolean approved, String reason) {}

    /** 对待审课程作出通过或驳回结论，并保存本次审核记录。 */
    @Operation(summary = "审核课程", description = "驳回时必须填写原因")
    @PostMapping("/courseaudit/review/{courseId}")
    public RestResponse reviewCourse(
        @PathVariable Long courseId,
        @RequestBody ReviewDecision decision
    ) {
        if (decision == null || decision.approved() == null) {
            GlobalException.cast("请选择审核结果");
        }
        coursePublishService.reviewCourse(
            companyId,
            courseId,
            decision.approved(),
            decision.reason(),
            reviewerName
        );
        return RestResponse.success();
    }

    /** 查看本机构指定课程的历次审核结论，供审核工作台追踪操作。 */
    @GetMapping("/courseaudit/history/{courseId}")
    public List<Map<String, Object>> auditHistory(@PathVariable Long courseId) {
        return coursePublishService.auditHistory(companyId, courseId);
    }

    /**
     * 使用配置机构编号请求发布课程，返回业务处理结果；发布资格由服务层检查。
     */
    @Operation(summary = "发布课程", description = "将审核通过的课程进行发布，发布后学员可查看学习")
    @PostMapping("/coursepublish/{courseId}")
    public RestResponse coursepublish(@PathVariable("courseId") Long courseId) {
        coursePublishService.publishCourse(companyId, courseId);
        return RestResponse.success();
    }

    /**
     * 下架本机构已发布课程，使学员端公开查询立即不再返回该课程。
     */
    @Operation(summary = "下架课程", description = "已发布课程须先下架，之后才能删除")
    @PutMapping("/coursepublish/{courseId}/offline")
    public RestResponse offlineCourse(@PathVariable Long courseId) {
        coursePublishService.offlineCourse(companyId, courseId);
        return RestResponse.success();
    }
}
