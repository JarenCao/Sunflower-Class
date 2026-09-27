package com.sunflower_class.service.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunflower_class.model.dto.CourseCategoryTreeDto;
import com.sunflower_class.model.po.CourseCategory;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * 课程分类的数据访问映射；通用增删改查由 MyBatis-Plus 提供，自定义查询另行声明。
 */
@Mapper
public interface CourseCategoryMapper extends BaseMapper<CourseCategory> {
    /**
     * 按父分类编号查询分类节点，供课程分类树展示与组装使用。
     */
    public List<CourseCategoryTreeDto> queryTreeNodes(String id);
}
