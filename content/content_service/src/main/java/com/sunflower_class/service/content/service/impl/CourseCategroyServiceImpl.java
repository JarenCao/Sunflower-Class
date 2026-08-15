package com.sunflower_class.service.content.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sunflower_class.model.dto.CourseCategoryTreeDto;
import com.sunflower_class.model.po.CourseCategory;
import com.sunflower_class.service.content.mapper.CourseCategoryMapper;
import com.sunflower_class.service.content.service.CourseCategroyService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CourseCategroyServiceImpl implements CourseCategroyService {

    @Autowired
    private CourseCategoryMapper courseCategoryMapper;

    @Override
    public List<CourseCategoryTreeDto> queryTreeNodes(String id) {
        log.info("查询课程分类树节点，父级ID: {}", id);

        if (id == null || id.trim().isEmpty()) {
            log.warn("ID参数为空，返回空列表");
            return new ArrayList<>();
        }
        List<CourseCategoryTreeDto> courseCategoryTreeDtos = courseCategoryMapper.queryTreeNodes(id);

        Map<String, List<CourseCategoryTreeDto>> parentMap = courseCategoryTreeDtos.stream()
                .collect(Collectors.groupingBy(CourseCategory::getParentid));

        courseCategoryTreeDtos.forEach(
                item -> item.setChildrenTreeNodes(parentMap.getOrDefault(item.getId(), Collections.emptyList())));
                
        return courseCategoryTreeDtos.stream()
                .filter(item -> "0".equals(item.getParentid()) || item.getParentid() == null)
                .flatMap(root->root.getChildrenTreeNodes().stream())
                .collect(Collectors.toList());
    }
}