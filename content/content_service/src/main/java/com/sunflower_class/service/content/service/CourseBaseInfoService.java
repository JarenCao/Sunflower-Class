package com.sunflower_class.service.content.service;

import com.sunflower_class.base.model.PageParams;
import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.model.dto.AddCourseDto;
import com.sunflower_class.model.dto.CourseBaseInfoDto;
import com.sunflower_class.model.dto.EditCourseDto;
import com.sunflower_class.model.dto.QueryCourseParamsDto;
import com.sunflower_class.model.po.CourseBase;

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
}
