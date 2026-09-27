package com.sunflower_class.service.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunflower_class.model.dto.TeachPlanDto;
import com.sunflower_class.model.po.Teachplan;
import java.util.List;

/**
 * 教学计划的数据访问映射；通用增删改查由 MyBatis-Plus 提供，自定义查询另行声明。
 */
public interface TeachplanMapper extends BaseMapper<Teachplan> {
    /**
     * 执行教学计划树映射查询，按课程编号组装章节、小节及关联媒资。
     */
    public List<TeachPlanDto> queryTreeNodes(Long id);
}
