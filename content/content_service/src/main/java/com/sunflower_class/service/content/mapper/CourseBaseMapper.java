package com.sunflower_class.service.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunflower_class.model.po.CourseBase;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 课程基础的数据访问映射；通用增删改查由 MyBatis-Plus 提供，自定义查询另行声明。
 */
public interface CourseBaseMapper extends BaseMapper<CourseBase> {
    /** 删除指定课程的审核记录，避免课程删除后留下无效关联。 */
    @Delete("DELETE FROM course_audit WHERE course_id = #{courseId}")
    int deleteCourseAudit(@Param("courseId") Long courseId);

    /** 保存一次审核结论及审核意见，保留课程多次提交后的历史。 */
    @Insert(
        "INSERT INTO course_audit(course_id, audit_mind, audit_status, audit_people, audit_date) " +
            "VALUES(#{courseId}, #{reason}, #{status}, #{reviewer}, #{reviewedAt})"
    )
    int insertCourseAudit(
        @Param("courseId") Long courseId,
        @Param("reason") String reason,
        @Param("status") String status,
        @Param("reviewer") String reviewer,
        @Param("reviewedAt") LocalDateTime reviewedAt
    );

    /** 按时间倒序读取课程审核记录，字段名供机构端直接使用。 */
    @Select(
        "SELECT id, course_id AS courseId, audit_mind AS reason, " +
            "audit_status AS status, audit_people AS reviewer, audit_date AS reviewedAt " +
            "FROM course_audit WHERE course_id = #{courseId} ORDER BY audit_date DESC, id DESC"
    )
    List<Map<String, Object>> selectCourseAuditHistory(@Param("courseId") Long courseId);

    /** 按课程或所属计划清理作业关联，兼容历史记录未填写 course_id 的情况。 */
    @Delete(
        "DELETE FROM teachplan_work WHERE course_id = #{courseId} " +
            "OR teachplan_id IN (SELECT id FROM teachplan WHERE course_id = #{courseId})"
    )
    int deleteTeachplanWork(@Param("courseId") Long courseId);
}
