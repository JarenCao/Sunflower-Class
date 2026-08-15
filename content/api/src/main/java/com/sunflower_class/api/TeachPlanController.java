package com.sunflower_class.api;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sunflower_class.model.dto.AddTeachPlanDto;
import com.sunflower_class.model.dto.TeachPlanDto;
import com.sunflower_class.service.content.service.TeachPlanService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@Tag(name = "课程计划", description = "课程计划相关接口")
public class TeachPlanController {

    @Autowired
    private TeachPlanService teachPlanService;

    @Operation(summary = "查询课程计划树", description = "根据课程ID查询课程计划树形结构")
    @GetMapping("techplan/{id}/tree-nodes")
    public List<TeachPlanDto> getTreeNodes(@PathVariable Long id) {
        return teachPlanService.findTeachPlanTree(id);
    }

    @Operation(summary = "创建或修改课程计划", description = "创建或修改课程计划（章节/小节/课时）")
    @PostMapping("/teachplan")
    public void addTeachPlan(@RequestBody @Valid AddTeachPlanDto addTeachPlanDto) {
        teachPlanService.saveTeachPlan(addTeachPlanDto);
    }
}