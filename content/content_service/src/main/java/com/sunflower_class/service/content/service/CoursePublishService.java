package com.sunflower_class.service.content.service;

public interface CoursePublishService {

    public void commitAudit(Long companyId, Long courseId);

    public void publishCourse(Long companyId, Long courseId);
}
