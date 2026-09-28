package com.sunflower_class.service.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunflower_class.model.po.CourseBase;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * 课程基础的数据访问映射；通用增删改查由 MyBatis-Plus 提供，自定义查询另行声明。
 */
public interface CourseBaseMapper extends BaseMapper<CourseBase> {
    /** 删除指定课程的审核记录，避免课程删除后留下无效关联。 */
    @Delete("DELETE FROM course_audit WHERE course_id = #{courseId}")
    int deleteCourseAudit(@Param("courseId") Long courseId);

    /** 按课程或所属计划清理作业关联，兼容历史记录未填写 course_id 的情况。 */
    @Delete(
        "DELETE FROM teachplan_work WHERE course_id = #{courseId} " +
            "OR teachplan_id IN (SELECT id FROM teachplan WHERE course_id = #{courseId})"
    )
    int deleteTeachplanWork(@Param("courseId") Long courseId);
}
