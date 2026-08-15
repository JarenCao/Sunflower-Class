package com.sunflower_class.service;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.sunflower_class.model.dto.CourseCategoryTreeDto;
import com.sunflower_class.service.content.mapper.CourseCategoryMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest
public class CourseCategoryTest {

    @Autowired
    CourseCategoryMapper categoryMapper;

    @Test
    public void testContentMapper() {
        List<CourseCategoryTreeDto> result = categoryMapper.queryTreeNodes("1");
        System.out.println(result);
    }
}
