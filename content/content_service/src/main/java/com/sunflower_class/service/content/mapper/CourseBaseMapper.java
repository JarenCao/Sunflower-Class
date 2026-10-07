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

    /** 全局媒资绑定数量；查询失败直接返回错误。 */
    @Select("SELECT COUNT(*) FROM teachplan_media WHERE media_id=#{id}")
    long countMediaBindings(@Param("id") String id);

    /** 封面保持原路径和资源地址匹配规则。 */
    @Select(
        "SELECT (SELECT COUNT(*) FROM course_base WHERE pic LIKE #{pattern} OR (pic=#{url} AND #{url}<>'')) + (SELECT COUNT(*) FROM course_teacher WHERE photograph LIKE #{pattern} OR (photograph=#{url} AND #{url}<>''))"
    )
    long countMediaCovers(@Param("pattern") String pattern, @Param("url") String url);

    /** 已发布快照中的目录和封面共同阻止误删。 */
    @Select(
        "SELECT COUNT(*) FROM course_publish WHERE status='30502' AND ((JSON_VALID(teachplan) AND JSON_SEARCH(teachplan,'one',#{id}) IS NOT NULL) OR pic LIKE #{pattern} OR (pic=#{url} AND #{url}<>'') OR (JSON_VALID(teachers) AND (JSON_SEARCH(teachers,'one',#{pattern}) IS NOT NULL OR (#{url}<>'' AND JSON_SEARCH(teachers,'one',#{url}) IS NOT NULL))))"
    )
    long countPublishedMediaReferences(
        @Param("id") String id,
        @Param("pattern") String pattern,
        @Param("url") String url
    );

    /** 待审核或审核通过的快照保留原资源引用。 */
    @Select(
        "SELECT COUNT(*) FROM course_publish_pre WHERE status IN ('30403','30404') AND ((JSON_VALID(teachplan) AND JSON_SEARCH(teachplan,'one',#{id}) IS NOT NULL) OR pic LIKE #{pattern} OR (pic=#{url} AND #{url}<>'') OR (JSON_VALID(teachers) AND (JSON_SEARCH(teachers,'one',#{pattern}) IS NOT NULL OR (#{url}<>'' AND JSON_SEARCH(teachers,'one',#{url}) IS NOT NULL))))"
    )
    long countAuditMediaReferences(
        @Param("id") String id,
        @Param("pattern") String pattern,
        @Param("url") String url
    );
}
