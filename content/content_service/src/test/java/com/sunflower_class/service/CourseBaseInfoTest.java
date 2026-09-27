package com.sunflower_class.service;

import com.sunflower_class.base.model.PageParams;
import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.model.dto.QueryCourseParamsDto;
import com.sunflower_class.model.po.CourseBase;
import com.sunflower_class.service.content.service.CourseBaseInfoService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 真实数据库课程分页查询示例，当前以打印结果进行人工检查。
 */
@Slf4j
@SpringBootTest
public class CourseBaseInfoTest {

    @Autowired
    CourseBaseInfoService courseBaseInfoService;

    /**
     * 调用课程分页服务并打印结果，用于观察真实数据库查询；当前没有断言。
     */
    @Test
    public void testCourseBaseInfo() {
        QueryCourseParamsDto courseParamsDto = new QueryCourseParamsDto();
        courseParamsDto.setCourseName("java");
        courseParamsDto.setAuditStatus("30404");
        courseParamsDto.setPublishStatus("30502");
        PageParams pageParams = new PageParams();
        pageParams.setPageNo(1L);
        pageParams.setPageSize(10L);
        var queryCourseBasePage = courseBaseInfoService.queryCourseBasePage(
            pageParams,
            courseParamsDto
        );

        System.out.println(queryCourseBasePage);
    }
}
