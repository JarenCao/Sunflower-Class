package com.sunflower_class.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunflower_class.base.model.PageParams;
import com.sunflower_class.model.po.*;
import com.sunflower_class.service.content.mapper.*;
import com.sunflower_class.service.content.service.impl.CourseBaseInfoServiceImpl;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 课程列表补充营销信息的回归测试，覆盖缺失营销记录的情况。
 */
class CourseListChargeTest {

    /**
     * 验证分页课程能批量补充收费信息，同时保留缺少营销记录的课程。
     */
    @Test
    void joinsMarketingWithoutLosingCoursesThatHaveNoMarketing() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
            new org.apache.ibatis.builder.MapperBuilderAssistant(
                new com.baomidou.mybatisplus.core.MybatisConfiguration(),
                "test"
            ),
            CourseMarket.class
        );
        var courses = mock(CourseBaseMapper.class);
        var markets = mock(CourseMarketMapper.class);
        var service = new CourseBaseInfoServiceImpl();
        ReflectionTestUtils.setField(service, "courseBaseMapper", courses);
        ReflectionTestUtils.setField(service, "courseMarketMapper", markets);
        var free = new CourseBase();
        free.setId(1L);
        var paid = new CourseBase();
        paid.setId(2L);
        var missing = new CourseBase();
        missing.setId(3L);
        var result = new Page<CourseBase>(1, 10, 3);
        result.setRecords(List.of(free, paid, missing));
        when(courses.selectPage(any(Page.class), any())).thenReturn(result);
        var fm = new CourseMarket();
        fm.setId(1L);
        fm.setCharge("30201");
        fm.setPrice(BigDecimal.ZERO);
        var pm = new CourseMarket();
        pm.setId(2L);
        pm.setCharge("30202");
        pm.setPrice(BigDecimal.TEN);
        when(markets.selectList(any())).thenReturn(List.of(pm, fm));
        var page = service.queryCourseBasePage(new PageParams(), null);
        assertEquals(3, page.getCount());
        assertEquals("30201", page.getItems().get(0).getCharge());
        assertEquals("30202", page.getItems().get(1).getCharge());
        assertEquals(BigDecimal.TEN, page.getItems().get(1).getPrice());
        assertNull(page.getItems().get(2).getCharge());
        verify(markets, times(1)).selectList(any());
    }
}
