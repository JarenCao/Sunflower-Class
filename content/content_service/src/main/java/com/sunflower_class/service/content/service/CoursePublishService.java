package com.sunflower_class.service.content.service;

/**
 * 提交审核、课程发布和下架的业务契约，调用方需提供机构及课程编号。
 */
public interface CoursePublishService {
    /**
     * 校验机构归属、当前审核状态、教学计划及营销信息，生成待审核快照并更新审核状态。
     */
    public void commitAudit(Long companyId, Long courseId);

    /**
     * 校验预发布快照的机构归属和审核通过状态，保存正式快照、更新发布状态并删除预发布记录。
     */
    public void publishCourse(Long companyId, Long courseId);

    /**
     * 将本机构已发布课程及其公开快照同时下架，供后续安全删除。
     */
    void offlineCourse(Long companyId, Long courseId);
}
