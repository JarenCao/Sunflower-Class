package com.sunflower_class.api;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sunflower_class.model.dto.CourseCategoryTreeDto;
import com.sunflower_class.service.content.service.CourseCategroyService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@CrossOrigin(origins = "*")
@RestController
@Tag(name = "课程分类", description = "课程分类信息相关接口")
public class CourseCategoryController {

    @Autowired
    private CourseCategroyService courseCategroyService;

    @Operation(summary = "查询课程分类树", description = "根据父节点ID查询课程分类树形结构")
    @GetMapping("/category/node")
    public List<CourseCategoryTreeDto> queryTreeNodes(@RequestParam(required = false) String id) {
        return courseCategroyService.queryTreeNodes(id);
    }
}