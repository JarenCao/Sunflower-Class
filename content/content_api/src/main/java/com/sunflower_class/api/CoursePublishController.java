package com.sunflower_class.api;

import com.sunflower_class.base.exception.GlobalException;
import com.sunflower_class.base.model.PageParams;
import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.base.model.RestResponse;
import com.sunflower_class.base.security.CurrentUser;
import com.sunflower_class.model.dto.CourseBaseInfoDto;
import com.sunflower_class.model.dto.ReviewDecisionDto;
import com.sunflower_class.model.po.CoursePublish;
import com.sunflower_class.model.po.CoursePublishPre;
import com.sunflower_class.service.content.service.CoursePublishService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private CoursePublishService coursePublishService;

    /** 媒资服务只公开被正式发布快照引用的封面，不开放未发布图片或视频。 */
    @Operation(
        summary = "核对已发布课程封面",
        description = "仅核对正式发布快照引用的图片，不开放草稿图片或视频。"
    )
    @GetMapping("/published-courses/covers/{mediaId}")
    public boolean isPublishedCover(@PathVariable String mediaId) {
        return coursePublishService.isPublishedCover(mediaId);
    }

    /** 学员只读取已发布快照，管理端草稿修改不会直接替换这里的数据。 */
    @Operation(
        summary = "分页查询已发布课程",
        description = "公开读取正式快照，草稿编辑不影响已发布内容。"
    )
    @GetMapping("/published-courses")
    public PageResult<CoursePublish> publishedCourses(
        @RequestParam(defaultValue = "1") long pageNo,
        @RequestParam(defaultValue = "20") long pageSize,
        @RequestParam(defaultValue = "") String q
    ) {
        return coursePublishService.publishedCourses(pageNo, pageSize, q);
    }

    /**
     * 读取指定编号的已发布课程快照，未发布或不存在的课程不返回详情。
     */
    @Operation(
        summary = "查询已发布课程详情",
        description = "未发布、已下架或不存在的课程不返回详情。"
    )
    @GetMapping("/published-courses/{id}")
    public CoursePublish publishedCourse(@PathVariable Long id) {
        return coursePublishService.publishedCourse(id);
    }

    /**
     * 校验机构归属、当前审核状态、教学计划及营销信息，生成待审核快照并更新审核状态。
     */
    @Operation(summary = "提交课程审核", description = "将指定课程提交审核，审核通过后可发布")
    @PostMapping("/courseaudit/commit/{courseId}")
    public RestResponse commitAudit(@PathVariable("courseId") Long courseId) {
        coursePublishService.commitAudit(CurrentUser.companyId(), courseId);
        return RestResponse.success();
    }

    /** 对待审课程作出通过或驳回结论，并保存本次审核记录。 */
    @Operation(summary = "审核课程", description = "驳回时必须填写原因")
    @PostMapping("/courseaudit/review/{courseId}")
    public RestResponse reviewCourse(
        @PathVariable Long courseId,
        @RequestBody ReviewDecisionDto decision
    ) {
        if (decision == null || decision.getApproved() == null) {
            GlobalException.cast("请选择审核结果");
        }
        coursePublishService.reviewCourse(courseId, decision.getApproved(), decision.getReason());
        return RestResponse.success();
    }

    /** 平台管理员查询全部课程审核历史，老师仅查询本人教学空间的课程。 */
    @Operation(
        summary = "查询课程审核历史",
        description = "平台管理员可查询全部课程，老师仅可查询本人教学空间课程。"
    )
    @GetMapping("/courseaudit/history/{courseId}")
    public List<Map<String, Object>> auditHistory(@PathVariable Long courseId) {
        return coursePublishService.auditHistory(
            "admin".equals(CurrentUser.jwt().getClaimAsString("role"))
                ? null
                : CurrentUser.companyId(),
            courseId
        );
    }

    /** 平台管理员使用独立审核入口，老师编辑入口继续核对教学空间归属。 */
    @Operation(
        summary = "分页查询课程审核队列",
        description = "仅平台管理员可访问，支持审核状态筛选。"
    )
    @GetMapping("/courseaudit/queue")
    public PageResult<CourseBaseInfoDto> auditQueue(
        PageParams page,
        @RequestParam(defaultValue = "30403") String status
    ) {
        return coursePublishService.auditQueue(page, status);
    }

    @Operation(
        summary = "查询课程待审快照",
        description = "平台管理员读取提交时冻结的课程、目录和师资信息。"
    )
    @GetMapping("/courseaudit/detail/{courseId}")
    public CoursePublishPre auditDetail(@PathVariable Long courseId) {
        return coursePublishService.auditDetail(courseId);
    }

    /**
     * 使用当前登录机构编号请求发布课程，返回业务处理结果；发布资格由服务层检查。
     */
    @Operation(summary = "发布课程", description = "将审核通过的课程进行发布，发布后学员可查看学习")
    @PostMapping("/coursepublish/{courseId}")
    public RestResponse coursepublish(@PathVariable("courseId") Long courseId) {
        coursePublishService.publishCourse(CurrentUser.companyId(), courseId);
        return RestResponse.success();
    }

    /**
     * 下架本机构已发布课程，使学员端公开查询立即不再返回该课程。
     */
    @Operation(summary = "下架课程", description = "已发布课程须先下架，之后才能删除")
    @PutMapping("/coursepublish/{courseId}/offline")
    public RestResponse offlineCourse(@PathVariable Long courseId) {
        coursePublishService.offlineCourse(CurrentUser.companyId(), courseId);
        return RestResponse.success();
    }
}
