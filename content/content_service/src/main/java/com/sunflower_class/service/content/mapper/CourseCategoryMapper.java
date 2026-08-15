package com.sunflower_class.service.content.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunflower_class.model.dto.CourseCategoryTreeDto;
import com.sunflower_class.model.po.CourseCategory;

@Mapper
public interface CourseCategoryMapper extends BaseMapper<CourseCategory> {

    public List<CourseCategoryTreeDto> queryTreeNodes(String id);
}
