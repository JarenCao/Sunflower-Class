package com.sunflower_class.service;

import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunflower_class.base.model.PageParams;
import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.model.dto.QueryCourseParamsDto;
import com.sunflower_class.model.po.CourseBase;
import com.sunflower_class.service.content.mapper.CourseBaseMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest
public class ContentMapperTest {

    @Autowired
    CourseBaseMapper courseBaseMapper;

    @Test
    public void testContentMapper() {

        QueryCourseParamsDto courseParamsDto = new QueryCourseParamsDto();
        courseParamsDto.setCourseName("java");

        LambdaQueryWrapper<CourseBase> lambdaQueryWrapper = new LambdaQueryWrapper<>();

        lambdaQueryWrapper.like(StringUtils.isNotBlank(courseParamsDto.getCourseName()), CourseBase::getName,
                courseParamsDto.getCourseName());

        lambdaQueryWrapper.eq(StringUtils.isNotBlank(courseParamsDto.getAuditStatus()), CourseBase::getAuditStatus,
                courseParamsDto.getAuditStatus());

        PageParams pageParams = new PageParams();
        pageParams.setPageNo(1L);
        pageParams.setPageSize(10L);

        Page<CourseBase> page = new Page<>(pageParams.getPageNo(), pageParams.getPageSize());

        Page<CourseBase> selectPage = courseBaseMapper.selectPage(page, lambdaQueryWrapper);

        List<CourseBase> items = selectPage.getRecords();

        Long total = selectPage.getTotal();

        PageResult<CourseBase> pageResult = new PageResult<CourseBase>(items, total, page.getCurrent(), page.getSize());

        Assertions.assertThat(pageResult).isNotNull();

        System.out.println("总条数: " + total);
        System.out.println("当前页: " + page.getCurrent());
        System.out.println("每页条数: " + page.getSize());
        System.out.println("数据列表: " + items);
    }
}
