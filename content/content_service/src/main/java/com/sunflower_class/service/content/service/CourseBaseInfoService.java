package com.sunflower_class.service.content.service;

import com.sunflower_class.base.model.PageParams;
import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.model.dto.AddCourseDto;
import com.sunflower_class.model.dto.CourseBaseInfoDto;
import com.sunflower_class.model.dto.CourseTeacherDto;
import com.sunflower_class.model.dto.EditCourseDto;
import com.sunflower_class.model.dto.QueryCourseParamsDto;
import com.sunflower_class.model.po.CourseTeacher;
import java.util.List;
import java.util.Map;

/**
 * 课程基础与营销信息的查询、新建、修改及事务删除业务契约。
 */
public interface CourseBaseInfoService {
    /**
     * 课程分页查询
     *
     * @param pageParams           分页查询参数
     * @param queryCourseParamsDto 查询条件
     * @return 查询结果
     */
    public PageResult<CourseBaseInfoDto> queryCourseBasePage(
        PageParams pageParams,
        QueryCourseParamsDto queryCourseParamsDto
    );

    /**
     * 校验必填项及业务编码，创建课程基础与营销记录，并返回包含分类名称的详情。
     */
    CourseBaseInfoDto createCourseBase(AddCourseDto addCourseDto);

    /**
     * 按课程编号查询基础与营销信息并组合详情；缺失记录时抛出业务异常。
     */
    CourseBaseInfoDto getCourseById(Long id);

    /**
     * 校验编辑表单并使用机构编号更新课程，返回更新后的基础和营销信息。
     */
    CourseBaseInfoDto updateCourseBaseInfo(Long companyId, EditCourseDto editCourseDto);

    /**
     * 校验机构归属和发布状态，在同一事务内删除课程及其关联数据。
     */
    void deleteCourse(Long companyId, Long courseId);
    /** 读取本机构课程的讲师介绍，独立于教师登录账号。 */
    List<CourseTeacher> listCourseTeachers(Long courseId);
    /** 保存讲师介绍，并使旧审核结论失效。 */
    CourseTeacher saveCourseTeacher(Long courseId, Long teacherId, CourseTeacherDto input);
    /** 删除本课程讲师介绍，不删除教师登录账号。 */
    void deleteCourseTeacher(Long courseId, Long teacherId);

    /** 在课程库核对媒资的草稿、封面、审核和发布引用，只返回数量。 */
    Map<String, Long> mediaReferences(String id, String url);
}
