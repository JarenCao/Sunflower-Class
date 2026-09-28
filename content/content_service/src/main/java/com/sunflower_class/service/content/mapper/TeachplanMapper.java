package com.sunflower_class.service.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunflower_class.model.dto.TeachPlanDto;
import com.sunflower_class.model.po.Teachplan;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * 教学计划的数据访问映射；通用增删改查由 MyBatis-Plus 提供，自定义查询另行声明。
 */
public interface TeachplanMapper extends BaseMapper<Teachplan> {
    /**
     * 执行教学计划树映射查询，按课程编号组装章节、小节及关联媒资。
     */
    public List<TeachPlanDto> queryTreeNodes(Long id);

    /** 清理指定章或小节及其直接子节点的作业关联，兼容历史空 course_id。 */
    @Delete(
        "DELETE FROM teachplan_work WHERE teachplan_id = #{id} " +
            "OR teachplan_id IN (SELECT id FROM teachplan WHERE parentid = #{id})"
    )
    int deleteRelatedWork(@Param("id") Long id);
}
