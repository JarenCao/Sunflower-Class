package com.sunflower_class.service.content.service;

import com.sunflower_class.model.dto.CourseCategoryTreeDto;
import java.util.List;

/**
 * 课程分类树查询的业务契约。
 */
public interface CourseCategroyService {
    /**
     * 查询课程分类树
     *
     * @param id 父节点ID，为空时查询根节点
     * @return 课程分类树形结构列表
     */
    List<CourseCategoryTreeDto> queryTreeNodes(String id);
}
