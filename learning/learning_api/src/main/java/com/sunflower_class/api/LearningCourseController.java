package com.sunflower_class.api;

import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.base.payment.CourseOrderSnapshotDto;
import com.sunflower_class.model.dto.CourseDirectoryDto;
import com.sunflower_class.model.dto.CourseEnrollmentDto;
import com.sunflower_class.model.dto.CoursePlaybackGrantDto;
import com.sunflower_class.service.learning.service.LearningCourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 提供公开课程目录，不在接口层代替学习资格或播放权限校验。 */
@Tag(name = "学员选课与学习")
@RestController
public class LearningCourseController {

    @Autowired
    private LearningCourseService service;

    /** 通过学习服务的发布副本返回实际目录。 */
    @Operation(summary = "读取已发布课程目录")
    @GetMapping("/courses/{id}/directory")
    public CourseDirectoryDto directory(@PathVariable long id) {
        return service.directory(id);
    }

    /** 学员编号只由服务层读取已验签身份，正文无法冒充其他学生。 */
    @Operation(summary = "选课")
    @PostMapping("/enrollments/{id}")
    public CourseEnrollmentDto enroll(@PathVariable long id) {
        return service.enroll(id);
    }

    /** 续期仅操作登录学员本人，正文中的用户和有效期参数不参与计算。 */
    @Operation(summary = "续期本人已到期免费课程")
    @PostMapping("/enrollments/{id}/renew")
    public CourseEnrollmentDto renew(@PathVariable long id) {
        return service.renew(id);
    }

    /** 查询当前学员自己的选课状态和资格。 */
    @Operation(summary = "查询当前课程学习资格")
    @GetMapping("/enrollments/{id}")
    public CourseEnrollmentDto enrollment(@PathVariable long id) {
        return service.enrollment(id);
    }

    /** 我的课程按服务端分页，不把全库记录交给浏览器过滤。 */
    @Operation(summary = "分页查询我的课程")
    @GetMapping("/enrollments")
    public PageResult<CourseEnrollmentDto> enrollments(
        @RequestParam(defaultValue = "1") long pageNo,
        @RequestParam(defaultValue = "10") long pageSize
    ) {
        return service.enrollments(pageNo, pageSize);
    }

    /** Media 转发原始学生 JWT 独立验签，任何调用者都不能传入其他学生身份。 */
    @Operation(summary = "校验课程小节播放资格")
    @GetMapping("/playback/{courseId}/{lessonId}")
    public ResponseEntity<CoursePlaybackGrantDto> playback(
        @PathVariable long courseId,
        @PathVariable long lessonId
    ) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(service.playbackGrant(courseId, lessonId));
    }

    /** 试学不接收媒资编号或客户端试学标记，只核对正式发布小节。 */
    @Operation(summary = "校验已发布试学小节")
    @GetMapping("/trial/{courseId}/{lessonId}")
    public ResponseEntity<CoursePlaybackGrantDto> trial(
        @PathVariable long courseId,
        @PathVariable long lessonId
    ) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(service.trialGrant(courseId, lessonId));
    }

    /** 订单服务转发原JWT，只能读取当前学生自身的待支付快照。 */
    @Operation(summary = "取得待支付选课快照")
    @GetMapping("/enrollments/{id}/order-snapshot")
    public ResponseEntity<CourseOrderSnapshotDto> orderSnapshot(@PathVariable long id) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(service.orderSnapshot(id));
    }
}
