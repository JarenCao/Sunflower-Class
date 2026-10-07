package com.sunflower_class.service.learning.service;

import com.sunflower_class.base.course.CourseEvent;
import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.base.payment.CourseOrderSnapshotDto;
import com.sunflower_class.base.payment.PaymentSuccessEvent;
import com.sunflower_class.model.dto.CourseDirectoryDto;
import com.sunflower_class.model.dto.CourseEnrollmentDto;
import com.sunflower_class.model.dto.CoursePlaybackGrantDto;

/** 学习服务维护发布目录和当前学员选课，视频地址由后续播放功能提供。 */
public interface LearningCourseService {
    /** 按事件版本事务更新目录，忽略重复和旧版本。 */
    void save(CourseEvent event);

    /** 获取已发布课程目录，未发布或已下架返回 404。 */
    CourseDirectoryDto directory(long courseId);
    /** 根据发布快照选课，学生身份和收费方式均不由请求传入。 */
    CourseEnrollmentDto enroll(long courseId);

    /** 本人已到期免费课程按最新发布有效期续期，重复请求不叠加天数。 */
    CourseEnrollmentDto renew(long courseId);

    /** 获取当前学员的选课和实时学习资格；未选课仍返回明确资格。 */
    CourseEnrollmentDto enrollment(long courseId);

    /** 按当前学员分页读取真实选课记录，包含下架课程和待支付记录。 */
    PageResult<CourseEnrollmentDto> enrollments(long page, long pageSize);
    /** 每次播放读取当前资格和正式目录，不能用任意媒资编号获得授权。 */
    CoursePlaybackGrantDto playbackGrant(long courseId, long lessonId);
    /** 匿名试学只允许正式发布快照中标记为试学的有效小节。 */
    CoursePlaybackGrantDto trialGrant(long courseId, long lessonId);
    /** 只返回当前学生待支付选课的不可变价格和有效期。 */
    CourseOrderSnapshotDto orderSnapshot(long courseId);
    /** 可靠支付事件在本地事务内开通资格并去重。 */
    void payment(PaymentSuccessEvent event);
}
