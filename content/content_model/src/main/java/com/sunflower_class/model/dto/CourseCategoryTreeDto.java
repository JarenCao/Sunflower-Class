package com.sunflower_class.model.dto;

import java.io.Serializable;
import java.util.List;

import com.sunflower_class.model.po.CourseCategory;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 课程分类树形结构 DTO
 */
@Data
@Schema(description = "课程分类树形结构")
public class CourseCategoryTreeDto extends CourseCategory implements Serializable {

    @Schema(description = "子节点列表（递归结构，支持无限层级）")
    private List<CourseCategoryTreeDto> childrenTreeNodes;
}