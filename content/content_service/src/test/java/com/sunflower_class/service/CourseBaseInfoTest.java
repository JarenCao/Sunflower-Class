package com.sunflower_class.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.sunflower_class.base.model.PageParams;
import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.model.dto.QueryCourseParamsDto;
import com.sunflower_class.model.po.CourseBase;
import com.sunflower_class.service.content.service.CourseBaseInfoService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest
public class CourseBaseInfoTest {

    @Autowired
    CourseBaseInfoService courseBaseInfoService;

    @Test
    public void testCourseBaseInfo() {
        QueryCourseParamsDto courseParamsDto = new QueryCourseParamsDto();
        courseParamsDto.setCourseName("java");
        courseParamsDto.setAuditStatus("30404");
        courseParamsDto.setPublishStatus("30502");
        PageParams pageParams = new PageParams();
        pageParams.setPageNo(1L);
        pageParams.setPageSize(10L);
        PageResult<CourseBase> queryCourseBasePage = courseBaseInfoService.queryCourseBasePage(pageParams, courseParamsDto);;
        
        System.out.println(queryCourseBasePage);
    }
}
