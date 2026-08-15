package com.sunflower_class.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sunflower_class.base.model.PageParams;
import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.model.dto.AddCourseDto;
import com.sunflower_class.model.dto.CourseBaseInfoDto;
import com.sunflower_class.model.dto.EditCourseDto;
import com.sunflower_class.model.dto.QueryCourseParamsDto;
import com.sunflower_class.model.po.CourseBase;
import com.sunflower_class.service.content.service.CourseBaseInfoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "课程管理", description = "课程基础信息相关接口")
public class CourseBaseInfoController {

    @Autowired
    private CourseBaseInfoService courseBaseInfoService;

    @Operation(summary = "查询课程列表", description = "根据条件分页查询课程信息")
    @PostMapping("/course/list")
    public PageResult<CourseBase> list(
            @RequestParam PageParams pageParams,
            @RequestBody QueryCourseParamsDto queryCourseParamsDto) {

        return courseBaseInfoService.queryCourseBasePage(pageParams, queryCourseParamsDto);
    }

    @Operation(summary = "添加课程", description = "添加课程基本信息")
    @PostMapping("/course")
    public CourseBaseInfoDto AddCourseBase(@RequestBody @Validated AddCourseDto addCourseDto) {
        return courseBaseInfoService.createCourseBase(addCourseDto);
    }

    @Operation(summary = "查询课程", description = "查询课程基本信息")
    @GetMapping("/course/{id}")
    public CourseBaseInfoDto selectCourseById(@PathVariable Long id) {
        return courseBaseInfoService.getCourseById(id);
    }

    @Operation(summary = "更新课程", description = "更新课程基本信息")
    @PutMapping("/course")
    public CourseBaseInfoDto updateCourseBaseInfo(
            @RequestParam Long companyId,
            @RequestBody @Validated EditCourseDto editCourseDto) {
        return courseBaseInfoService.updateCourseBaseInfo(companyId, editCourseDto);
    }
}