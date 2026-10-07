package com.sunflower_class.service.content.service;

import com.sunflower_class.base.model.PageParams;
import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.model.dto.CourseBaseInfoDto;
import com.sunflower_class.model.po.CoursePublish;
import com.sunflower_class.model.po.CoursePublishPre;
import com.sunflower_class.model.po.MqMessage;
import java.util.List;
import java.util.Map;

/**
 * 提交审核、课程发布和下架的业务契约，调用方需提供机构及课程编号。
 */
public interface CoursePublishService {
    /**
     * 校验机构归属、当前审核状态、教学计划及营销信息，生成待审核快照并更新审核状态。
     */
    public void commitAudit(Long companyId, Long courseId);

    /** 审核待审快照并记录审核人、时间和意见；驳回时必须填写原因。 */
    void reviewCourse(Long courseId, boolean approved, String reason);

    /** 平台管理员跨教学空间读取审核队列及只读提交快照。 */
    PageResult<CourseBaseInfoDto> auditQueue(PageParams page, String status);
    CoursePublishPre auditDetail(Long courseId);

    /** 平台管理员可读取全部审核历史，老师只能读取本人教学空间课程。 */
    List<Map<String, Object>> auditHistory(Long companyId, Long courseId);

    /**
     * 校验预发布快照的机构归属和审核通过状态，保存正式快照、更新发布状态并删除预发布记录。
     */
    public void publishCourse(Long companyId, Long courseId);

    /**
     * 将本机构已发布课程及其公开快照同时下架，供后续安全删除。
     */
    void offlineCourse(Long companyId, Long courseId);
    /** 已发布快照的原有只读查询。 */
    boolean isPublishedCover(String mediaId);
    /** 已发布快照的原有只读查询。 */
    PageResult<CoursePublish> publishedCourses(long pageNo, long pageSize, String q);
    /** 已发布快照的原有只读查询。 */
    CoursePublish publishedCourse(Long id);

    /** 当前机构的最新发布消息，不返回快照负载。 */
    List<MqMessage> publicationMessages(Long companyId, long courseId);
    /** 当前机构手动恢复未完成发布消息。 */
    void retryPublicationMessage(Long companyId, long id);
}
