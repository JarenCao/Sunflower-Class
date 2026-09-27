package com.sunflower_class.service.content.service.impl;

import com.sunflower_class.model.dto.CourseCategoryTreeDto;
import com.sunflower_class.model.po.CourseCategory;
import com.sunflower_class.service.content.mapper.CourseCategoryMapper;
import com.sunflower_class.service.content.service.CourseCategroyService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 按父子编号将数据库分类节点组装为前端可用的分类树。
 */
@Slf4j
@Service
public class CourseCategroyServiceImpl implements CourseCategroyService {

    @Autowired
    private CourseCategoryMapper courseCategoryMapper;

    /**
     * 查询分类节点并按父编号组装子节点，返回虚拟根下的分类；空编号返回空列表。
     */
    @Override
    public List<CourseCategoryTreeDto> queryTreeNodes(String id) {
        log.info("查询课程分类树节点，父级ID: {}", id);

        if (id == null || id.trim().isEmpty()) {
            log.warn("ID参数为空，返回空列表");
            return new ArrayList<>();
        }
        List<CourseCategoryTreeDto> courseCategoryTreeDtos = courseCategoryMapper.queryTreeNodes(
            id
        );

        Map<String, List<CourseCategoryTreeDto>> parentMap = courseCategoryTreeDtos
            .stream()
            .collect(Collectors.groupingBy(CourseCategory::getParentid));

        // 给每个分类填充子列表，无子节点时使用空列表。
        courseCategoryTreeDtos.forEach(item ->
            item.setChildrenTreeNodes(parentMap.getOrDefault(item.getId(), Collections.emptyList()))
        );

        return courseCategoryTreeDtos
            .stream()
            // 筛出虚拟根节点，随后只返回根节点的直接子分类。
            .filter(item -> "0".equals(item.getParentid()) || item.getParentid() == null)
            // 展开虚拟根的子列表，去掉页面无需展示的根层级。
            .flatMap(root -> root.getChildrenTreeNodes().stream())
            .collect(Collectors.toList());
    }
}
