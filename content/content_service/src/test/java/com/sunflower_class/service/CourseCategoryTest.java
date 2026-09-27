package com.sunflower_class.service;

import com.sunflower_class.model.dto.CourseCategoryTreeDto;
import com.sunflower_class.service.content.mapper.CourseCategoryMapper;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 真实数据库分类树查询示例，当前以打印结果进行人工检查。
 */
@Slf4j
@SpringBootTest
public class CourseCategoryTest {

    @Autowired
    CourseCategoryMapper categoryMapper;

    /**
     * 调用分类树查询并打印结果，用于连接真实数据库的手工观察；当前没有断言。
     */
    @Test
    public void testContentMapper() {
        List<CourseCategoryTreeDto> result = categoryMapper.queryTreeNodes("1");
        System.out.println(result);
    }
}
