package com.sunflower_class.service.search.service;

import com.sunflower_class.base.course.CourseEvent;
import com.sunflower_class.base.model.PageResult;
import java.util.List;
import java.util.Map;

/** Elasticsearch 课程检索与发布事件同步业务。 */
public interface CourseSearchService {
    /** 按外部事件版本更新索引，重复或旧事件不能覆盖新文档。 */
    void save(CourseEvent event);
    /** 按关键词和分类查询真实索引，返回后端分页结果。 */
    PageResult<Map<String, Object>> list(long page, long size, String query, String category);
    /** 获取所有实际已发布课程分类。 */
    List<String> categories();
    /** 获取已发布详情，已下架或不存在返回 404。 */
    Map<String, Object> get(long courseId);
}
