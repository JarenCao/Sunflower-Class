package com.sunflower_class.api;

import com.sunflower_class.base.model.PageParams;
import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.base.security.CurrentUser;
import com.sunflower_class.model.dto.AddCourseDto;
import com.sunflower_class.model.dto.CourseBaseInfoDto;
import com.sunflower_class.model.dto.CourseTeacherDto;
import com.sunflower_class.model.dto.EditCourseDto;
import com.sunflower_class.model.dto.QueryCourseParamsDto;
import com.sunflower_class.model.po.CourseTeacher;
import com.sunflower_class.service.content.service.CourseBaseInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 机构端课程基础信息接口，提供分页、创建、详情、修改及删除入口。
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/course")
@Tag(name = "课程管理", description = "课程基础信息相关接口")
public class CourseBaseInfoController {

    // 机构编号在请求时从已验证的登录身份获取。

    @Autowired
    private CourseBaseInfoService courseBaseInfoService;

    /**
     * 接收页码及课程筛选条件，返回当前登录机构下包含收费信息的课程分页结果。
     */
    @Operation(summary = "查询课程列表", description = "根据条件分页查询课程信息")
    // PageParams 从请求参数绑定，课程名称及状态等筛选条件从 JSON 请求体读取。
    @PostMapping("/list")
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
    @PostMapping
    public CourseBaseInfoDto AddCourseBase(@RequestBody @Validated AddCourseDto addCourseDto) {
        return courseBaseInfoService.createCourseBase(addCourseDto);
    }

    /**
     * 按课程编号读取本人教学空间的编辑详情；缺少营销记录时展示默认值。
     */
    @Operation(summary = "查询课程", description = "查询课程基本信息")
    @GetMapping("/{id}")
    public CourseBaseInfoDto selectCourseById(@PathVariable Long id) {
        return courseBaseInfoService.getCourseById(id);
    }

    /**
     * 校验编辑表单并使用机构编号更新课程，返回更新后的基础和营销信息。
     */
    @Operation(summary = "更新课程", description = "更新课程基本信息")
    // 先执行 DTO 参数校验，再由服务层比较当前登录机构编号与课程归属。
    @PutMapping
    public CourseBaseInfoDto updateCourseBaseInfo(
        @RequestBody @Validated EditCourseDto editCourseDto
    ) {
        return courseBaseInfoService.updateCourseBaseInfo(CurrentUser.companyId(), editCourseDto);
    }

    /**
     * 删除本机构未发布或已下架课程；已发布课程必须先下架。
     */
    @Operation(summary = "删除课程", description = "删除本机构未发布或已下架的课程及其关联记录")
    @DeleteMapping("/{id}")
    public void deleteCourse(@PathVariable Long id) {
        courseBaseInfoService.deleteCourse(CurrentUser.companyId(), id);
    }

    /** 课程师资仅描述授课人员，不改变登录账号或权限。 */
    @Operation(summary = "查询本机构课程师资")
    @GetMapping("/{courseId}/teachers")
    public List<CourseTeacher> teachers(@PathVariable Long courseId) {
        return courseBaseInfoService.listCourseTeachers(courseId);
    }

    @Operation(summary = "新增课程讲师介绍")
    @PostMapping("/{courseId}/teachers")
    public CourseTeacher addTeacher(
        @PathVariable Long courseId,
        @RequestBody @Validated CourseTeacherDto input,
        BindingResult validation
    ) {
        // 师资表单局部返回可读字段错误，保持其他接口的异常约定。
        if (validation.hasErrors()) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            validation.getAllErrors().getFirst().getDefaultMessage()
        );
        return courseBaseInfoService.saveCourseTeacher(courseId, null, input);
    }

    @Operation(summary = "修改课程讲师介绍")
    @PutMapping("/{courseId}/teachers/{teacherId}")
    public CourseTeacher editTeacher(
        @PathVariable Long courseId,
        @PathVariable Long teacherId,
        @RequestBody @Validated CourseTeacherDto input,
        BindingResult validation
    ) {
        // 师资表单局部返回可读字段错误，保持其他接口的异常约定。
        if (validation.hasErrors()) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            validation.getAllErrors().getFirst().getDefaultMessage()
        );
        return courseBaseInfoService.saveCourseTeacher(courseId, teacherId, input);
    }

    @Operation(summary = "删除课程讲师介绍")
    @DeleteMapping("/{courseId}/teachers/{teacherId}")
    public void deleteTeacher(@PathVariable Long courseId, @PathVariable Long teacherId) {
        courseBaseInfoService.deleteCourseTeacher(courseId, teacherId);
    }
}
