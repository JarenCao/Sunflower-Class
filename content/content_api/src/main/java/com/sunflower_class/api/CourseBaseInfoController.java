package com.sunflower_class.api;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 机构端课程基础信息接口，提供分页、创建、详情、修改及删除入口。
 */
@CrossOrigin(origins = "*")
@RestController
@Tag(name = "课程管理", description = "课程基础信息相关接口")
public class CourseBaseInfoController {

    // 当前机构编号来自配置，仅用于开发阶段；不能替代登录身份及完整的机构权限校验。
    @Value("${sunflower.company-id}")
    private Long companyId;

    @Autowired
    private CourseBaseInfoService courseBaseInfoService;

    /**
     * 接收页码及课程筛选条件，返回配置机构下包含收费信息的课程分页结果。
     */
    @Operation(summary = "查询课程列表", description = "根据条件分页查询课程信息")
    // PageParams 从请求参数绑定，课程名称及状态等筛选条件从 JSON 请求体读取。
    @PostMapping("/course/list")
    public PageResult<CourseBaseInfoDto> list(
        PageParams pageParams,
        @RequestBody QueryCourseParamsDto queryCourseParamsDto
    ) {
        return courseBaseInfoService.queryCourseBasePage(pageParams, queryCourseParamsDto);
    }

    /**
     * 接收并校验新增课程表单，调用服务层保存基础与营销信息，返回完整课程详情。
     */
    @Operation(summary = "添加课程", description = "添加课程基本信息")
    @PostMapping("/course")
    public CourseBaseInfoDto AddCourseBase(@RequestBody @Validated AddCourseDto addCourseDto) {
        return courseBaseInfoService.createCourseBase(addCourseDto);
    }

    /**
     * 按课程编号读取编辑详情；基础或营销记录缺失时由服务层抛出业务异常。
     */
    @Operation(summary = "查询课程", description = "查询课程基本信息")
    @GetMapping("/course/{id}")
    public CourseBaseInfoDto selectCourseById(@PathVariable Long id) {
        return courseBaseInfoService.getCourseById(id);
    }

    /**
     * 校验编辑表单并使用机构编号更新课程，返回更新后的基础和营销信息。
     */
    @Operation(summary = "更新课程", description = "更新课程基本信息")
    // 先执行 DTO 参数校验，再由服务层比较配置机构编号与课程归属。
    @PutMapping("/course")
    public CourseBaseInfoDto updateCourseBaseInfo(
        @RequestBody @Validated EditCourseDto editCourseDto
    ) {
        return courseBaseInfoService.updateCourseBaseInfo(companyId, editCourseDto);
    }

    /**
     * 删除本机构未发布或已下架课程；已发布课程必须先下架。
     */
    @Operation(summary = "删除课程", description = "删除本机构未发布或已下架的课程及其关联记录")
    @DeleteMapping("/course/{id}")
    public void deleteCourse(@PathVariable Long id) {
        courseBaseInfoService.deleteCourse(companyId, id);
    }
}
