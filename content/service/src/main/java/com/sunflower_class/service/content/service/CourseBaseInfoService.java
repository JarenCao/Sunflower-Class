package com.sunflower_class.service.content.service;

import com.sunflower_class.base.model.PageParams;
import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.model.dto.AddCourseDto;
import com.sunflower_class.model.dto.CourseBaseInfoDto;
import com.sunflower_class.model.dto.EditCourseDto;
import com.sunflower_class.model.dto.QueryCourseParamsDto;
import com.sunflower_class.model.po.CourseBase;

public interface CourseBaseInfoService {
    /**
     * 课程分页查询
     * 
     * @param pageParams           分页查询参数
     * @param queryCourseParamsDto 查询条件
     * @return 查询结果
     */
    public PageResult<CourseBase> queryCourseBasePage(PageParams pageParams, QueryCourseParamsDto queryCourseParamsDto);

    CourseBaseInfoDto createCourseBase(AddCourseDto addCourseDto);
    
    CourseBaseInfoDto getCourseById(Long id);
    
    CourseBaseInfoDto updateCourseBaseInfo(Long companyId, EditCourseDto editCourseDto);
}
