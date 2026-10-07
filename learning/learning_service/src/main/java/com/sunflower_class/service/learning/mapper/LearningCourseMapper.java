package com.sunflower_class.service.learning.mapper;

import com.sunflower_class.model.po.CourseEnrollment;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 按原三层架构集中保存业务 SQL，服务层只编排规则与事务。 */
public interface LearningCourseMapper {
    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Insert(
        "INSERT INTO learning_course (id,event_id,status,name,tags,category,payload) VALUES (#{courseId},#{eventId},#{status},#{name},#{tags},#{category},#{payload}) ON DUPLICATE KEY UPDATE status=IF(event_id<VALUES(event_id),VALUES(status),status), name=IF(event_id<VALUES(event_id),VALUES(name),name), tags=IF(event_id<VALUES(event_id),VALUES(tags),tags), category=IF(event_id<VALUES(event_id),VALUES(category),category), payload=IF(event_id<VALUES(event_id),VALUES(payload),payload), event_id=GREATEST(event_id,VALUES(event_id))"
    )
    int upsertPublishedCourse(
        @Param("courseId") Long courseId,
        @Param("eventId") Long eventId,
        @Param("status") String status,
        @Param("name") String name,
        @Param("tags") String tags,
        @Param("category") String category,
        @Param("payload") String payload
    );

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Select("SELECT payload FROM learning_course WHERE id=#{courseId} AND status='30502'")
    List<String> selectPublishedPayload(@Param("courseId") Long courseId);

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Select(
        "SELECT payload FROM learning_course WHERE id=#{courseId} AND status=#{status} FOR UPDATE"
    )
    List<String> selectPublishedPayloadForUpdate(
        @Param("courseId") Long courseId,
        @Param("status") String status
    );

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Insert(
        "INSERT INTO course_enrollment(user_id,course_id,course_name,enrollment_type,status,price,valid_days,expires_at) VALUES (#{userId},#{courseId},#{courseName},#{enrollmentType},#{status},#{price},#{validDays},IF(#{free} AND #{expiryDays}>0,DATE_ADD(CURRENT_TIMESTAMP,INTERVAL #{intervalDays} DAY),NULL))"
    )
    int insertEnrollment(
        @Param("userId") String userId,
        @Param("courseId") Long courseId,
        @Param("courseName") String courseName,
        @Param("enrollmentType") String enrollmentType,
        @Param("status") String status,
        @Param("price") BigDecimal price,
        @Param("validDays") Integer validDays,
        @Param("free") boolean free,
        @Param("expiryDays") Integer expiryDays,
        @Param("intervalDays") Integer intervalDays
    );

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Select("SELECT name FROM learning_course WHERE id=#{courseId} AND status=#{status}")
    List<String> selectPublishedName(
        @Param("courseId") Long courseId,
        @Param("status") String status
    );

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Select("SELECT COUNT(*) FROM course_enrollment WHERE user_id=#{userId}")
    Long countEnrollments(@Param("userId") String userId);

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Select("SELECT payload FROM learning_course WHERE id=#{courseId} AND status=#{status}")
    List<String> selectCoursePayload(
        @Param("courseId") Long courseId,
        @Param("status") String status
    );

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Select(
        "SELECT e.* FROM course_enrollment e JOIN learning_course c ON c.id=e.course_id WHERE e.user_id=#{userId} AND e.course_id=#{courseId} AND e.enrollment_type='70102' AND e.status='70202' AND c.status='30502'"
    )
    List<Map<String, Object>> selectPendingSnapshot(
        @Param("userId") String userId,
        @Param("courseId") Long courseId
    );

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Select("SELECT DATABASE()")
    String selectDatabase();

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Select("SELECT payload FROM payment_processed WHERE order_id=#{orderId}")
    List<String> selectProcessedPayload(@Param("orderId") Long orderId);

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Select(
        "SELECT * FROM course_enrollment WHERE user_id=#{userId} AND course_id=#{courseId} FOR UPDATE"
    )
    List<Map<String, Object>> selectEnrollmentForUpdate(
        @Param("userId") String userId,
        @Param("courseId") Long courseId
    );

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Select("SELECT payload FROM payment_processed WHERE order_id=#{orderId} FOR UPDATE")
    List<String> selectProcessedPayloadForUpdate(@Param("orderId") Long orderId);

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Update(
        "UPDATE course_enrollment SET status='70201',expires_at=#{expiresAt} WHERE user_id=#{userId} AND course_id=#{courseId} AND status='70202'"
    )
    int activateEnrollment(
        @Param("expiresAt") LocalDateTime expiresAt,
        @Param("userId") String userId,
        @Param("courseId") Long courseId
    );

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Insert(
        "INSERT INTO payment_processed(order_id,user_id,course_id,payload) VALUES (#{orderId},#{userId},#{courseId},#{payload})"
    )
    int insertProcessedPayment(
        @Param("orderId") Long orderId,
        @Param("userId") String userId,
        @Param("courseId") Long courseId,
        @Param("payload") String payload
    );

    /** 数据库时间决定续期起点，只更新本人已到期的免费成功记录。 */
    @Update(
        "UPDATE course_enrollment SET valid_days=#{days},expires_at=IF(#{days}>0,DATE_ADD(CURRENT_TIMESTAMP,INTERVAL #{days} DAY),NULL) WHERE user_id=#{userId} AND course_id=#{courseId} AND enrollment_type='70101' AND status='70201' AND expires_at IS NOT NULL AND expires_at<=CURRENT_TIMESTAMP"
    )
    int renewFreeEnrollment(
        @Param("userId") String userId,
        @Param("courseId") Long courseId,
        @Param("days") Integer days
    );

    /** 绑定分页和课程条件，数据库时间继续决定是否到期。 */
    @Select(
        "<script>SELECT e.*,NULLIF(JSON_UNQUOTE(JSON_EXTRACT(c.payload,'$.pic')),'null') AS pic,COALESCE(c.status=#{published},FALSE) AS available,(e.expires_at IS NOT NULL AND e.expires_at&lt;=CURRENT_TIMESTAMP) AS expired,(COALESCE(c.status=#{published},FALSE) AND e.enrollment_type='70101' AND e.status='70201' AND e.expires_at IS NOT NULL AND e.expires_at&lt;=CURRENT_TIMESTAMP AND JSON_UNQUOTE(JSON_EXTRACT(c.payload,'$.charge'))='30201') AS renewable FROM course_enrollment e LEFT JOIN learning_course c ON c.id=e.course_id WHERE e.user_id=#{userId}<if test='courseId != null'> AND e.course_id=#{courseId}</if><if test='size != null'> ORDER BY e.created_at DESC,e.course_id DESC LIMIT #{size} OFFSET #{offset}</if></script>"
    )
    List<CourseEnrollment> selectEnrollments(
        @Param("published") String published,
        @Param("userId") String userId,
        @Param("courseId") Long courseId,
        @Param("size") Long size,
        @Param("offset") Long offset
    );
}
