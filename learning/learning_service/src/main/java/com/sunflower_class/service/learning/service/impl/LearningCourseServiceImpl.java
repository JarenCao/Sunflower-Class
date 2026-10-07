package com.sunflower_class.service.learning.service.impl;

import static com.sunflower_class.base.model.BusinessCodes.CHARGE_FREE;
import static com.sunflower_class.base.model.BusinessCodes.CHARGE_PAID;
import static com.sunflower_class.base.model.BusinessCodes.COURSE_PUBLISHED;
import static com.sunflower_class.base.model.BusinessCodes.ENROLLMENT_FREE;
import static com.sunflower_class.base.model.BusinessCodes.ENROLLMENT_PAID;
import static com.sunflower_class.base.model.BusinessCodes.ENROLLMENT_PENDING;
import static com.sunflower_class.base.model.BusinessCodes.ENROLLMENT_SUCCESS;
import static com.sunflower_class.base.model.BusinessCodes.QUALIFICATION_ALLOWED;
import static com.sunflower_class.base.model.BusinessCodes.QUALIFICATION_EXPIRED;
import static com.sunflower_class.base.model.BusinessCodes.QUALIFICATION_NONE;

import com.sunflower_class.base.course.CourseEvent;
import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.base.payment.CourseOrderSnapshotDto;
import com.sunflower_class.base.payment.PaymentSuccessEvent;
import com.sunflower_class.base.security.CurrentUser;
import com.sunflower_class.model.dto.CourseDirectoryDto;
import com.sunflower_class.model.dto.CourseEnrollmentDto;
import com.sunflower_class.model.dto.CoursePlaybackGrantDto;
import com.sunflower_class.model.po.CourseEnrollment;
import com.sunflower_class.service.learning.mapper.LearningCourseMapper;
import com.sunflower_class.service.learning.service.LearningCourseService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 用独立目录表维护不可变课程快照，按课程主键及事件版本保证幂等。 */
@Service
public class LearningCourseServiceImpl implements LearningCourseService {

    @Autowired
    private LearningCourseMapper learningCourseMapper;

    @Autowired
    private JsonMapper json;

    /** 复用 Spring JDBC 和现有内容库数据源，不访问内容草稿表。 */

    /** 事务提交后共用消费者才回传成功；低版本事件不覆盖新目录或下架状态。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(CourseEvent event) {
        Map course = json.readValue(event.getSnapshot(), Map.class);
        learningCourseMapper.upsertPublishedCourse(
            event.getCourseId(),
            event.getEventId(),
            event.getStatus(),
            Objects.toString(course.get("name"), ""),
            Objects.toString(course.get("tags"), ""),
            Objects.toString(course.get("mtName"), "课程"),
            event.getSnapshot()
        );
    }

    /** 目录只读取本服务已发布副本，不能把草稿目录误当成可公开目录。 */
    @Override
    public CourseDirectoryDto directory(long courseId) {
        List<String> values = learningCourseMapper.selectPublishedPayload(courseId);
        if (values.isEmpty()) throw new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "课程未发布或不存在"
        );
        Map course = json.readValue(values.getFirst(), Map.class);
        return new CourseDirectoryDto(courseId, Objects.toString(course.get("teachplan"), "[]"));
    }

    /** 锁住当前发布副本，避免选课事务中课程被下架；唯一主键保证并发幂等。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseEnrollmentDto enroll(long courseId) {
        CurrentUser.requireRole("student");
        List<String> rows = learningCourseMapper.selectPublishedPayloadForUpdate(
            courseId,
            COURSE_PUBLISHED
        );
        if (rows.isEmpty()) throw new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "课程未发布或已下架"
        );
        List<CourseEnrollmentDto> existing = records(
            CurrentUser.jwt().getSubject(),
            courseId,
            null,
            null
        );
        if (!existing.isEmpty()) return existing.getFirst();
        Map course = json.readValue(rows.getFirst(), Map.class);
        String charge = Objects.toString(course.get("charge"), "");
        boolean free = CHARGE_FREE.equals(charge);
        if (!free && !CHARGE_PAID.equals(charge)) throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "课程收费方式异常，暂不能选课"
        );
        BigDecimal price;
        Integer days;
        try {
            price = free
                ? BigDecimal.ZERO
                : new BigDecimal(Objects.toString(course.get("price"), ""));
            days =
                course.get("validDays") == null
                    ? null
                    : Integer.valueOf(course.get("validDays").toString());
            if (
                price.signum() < 0 ||
                price.scale() > 2 ||
                price.compareTo(new BigDecimal("99999999.99")) > 0 ||
                (days != null && days < 0)
            ) throw new IllegalArgumentException();
        } catch (RuntimeException invalid) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "课程价格或有效期异常，暂不能选课"
            );
        }
        // 免费资格从本次选课开始计时；收费记录不能在支付前开通或设置有效期。
        learningCourseMapper.insertEnrollment(
            CurrentUser.jwt().getSubject(),
            courseId,
            Objects.toString(course.get("name"), ""),
            free ? ENROLLMENT_FREE : ENROLLMENT_PAID,
            free ? ENROLLMENT_SUCCESS : ENROLLMENT_PENDING,
            price,
            days,
            free,
            days,
            days
        );
        return records(CurrentUser.jwt().getSubject(), courseId, null, null).getFirst();
    }

    /** 先锁正式课程副本，再锁本人选课；与选课锁顺序一致，串行化并发续期。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseEnrollmentDto renew(long courseId) {
        CurrentUser.requireRole("student");
        String userId = CurrentUser.jwt().getSubject();
        List<String> payloads = learningCourseMapper.selectPublishedPayloadForUpdate(
            courseId,
            COURSE_PUBLISHED
        );
        if (payloads.isEmpty()) throw new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "课程未发布或已下架，不能续期"
        );
        JsonNode course = json.readTree(payloads.getFirst());
        if (!CHARGE_FREE.equals(course.path("charge").asText())) throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "课程已改为收费，不能免费续期"
        );
        List<Map<String, Object>> rows = learningCourseMapper.selectEnrollmentForUpdate(
            userId,
            courseId
        );
        if (
            rows.isEmpty() ||
            !ENROLLMENT_FREE.equals(rows.getFirst().get("enrollment_type")) ||
            !ENROLLMENT_SUCCESS.equals(rows.getFirst().get("status"))
        ) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "没有本人已开通的免费选课记录");
        }
        CourseEnrollmentDto current = records(userId, courseId, null, null).getFirst();
        // 有效记录保持原到期时间，重复点击和并发重放不能累加学习天数。
        if (!QUALIFICATION_EXPIRED.equals(current.getQualification())) return current;
        Integer days;
        try {
            days =
                course.path("validDays").isNull() || course.path("validDays").isMissingNode()
                    ? null
                    : Integer.valueOf(course.path("validDays").asText());
            if (
                days != null && (days < 0 || LocalDateTime.now().plusDays(days).getYear() > 9999)
            ) throw new IllegalArgumentException();
        } catch (RuntimeException invalid) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "发布课程有效期异常，暂不能续期"
            );
        }
        if (
            learningCourseMapper.renewFreeEnrollment(userId, courseId, days) != 1
        ) throw new ResponseStatusException(HttpStatus.CONFLICT, "资格状态已变化，请刷新后重试");
        return records(userId, courseId, null, null).getFirst();
    }

    /** 下架或过期时保留原选课记录，但实时资格不允许学习。 */
    @Override
    public CourseEnrollmentDto enrollment(long courseId) {
        CurrentUser.requireRole("student");
        List<CourseEnrollmentDto> rows = records(
            CurrentUser.jwt().getSubject(),
            courseId,
            null,
            null
        );
        if (!rows.isEmpty()) return rows.getFirst();
        List<String> course = learningCourseMapper.selectPublishedName(courseId, COURSE_PUBLISHED);
        if (course.isEmpty()) throw new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "课程未发布或不存在"
        );
        return new CourseEnrollmentDto(
            courseId,
            course.getFirst(),
            null,
            null,
            null,
            null,
            null,
            QUALIFICATION_NONE,
            true,
            false,
            null
        );
    }

    /** 用户条件贯穿计数和查询，客户端传入 userId 不会改变记录归属。 */
    @Override
    public PageResult<CourseEnrollmentDto> enrollments(long page, long pageSize) {
        CurrentUser.requireRole("student");
        if (
            page < 1 || page > Integer.MAX_VALUE || pageSize < 1 || pageSize > 100
        ) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数无效");
        String student = CurrentUser.jwt().getSubject();
        Long count = learningCourseMapper.countEnrollments(student);
        return new PageResult<>(
            records(student, null, pageSize, (page - 1) * pageSize),
            count,
            page,
            pageSize
        );
    }

    /** 资格由数据库时间与发布状态计算，不能把免费或价格为零等同于已选课。 */
    private List<CourseEnrollmentDto> records(
        String userId,
        Long courseId,
        Long size,
        Long offset
    ) {
        return learningCourseMapper
            .selectEnrollments(COURSE_PUBLISHED, userId, courseId, size, offset)
            .stream()
            .map(this::record)
            .toList();
    }

    /** 资格仍按课程上下架、到期时间和原选课状态计算。 */
    private CourseEnrollmentDto record(CourseEnrollment enrollment) {
        boolean available = enrollment.isAvailable();
        String qualification = !available
            ? QUALIFICATION_NONE
            : enrollment.isExpired()
              ? QUALIFICATION_EXPIRED
              : ENROLLMENT_SUCCESS.equals(enrollment.getStatus())
                ? QUALIFICATION_ALLOWED
                : QUALIFICATION_NONE;
        return new CourseEnrollmentDto(
            enrollment.getCourseId(),
            enrollment.getCourseName(),
            enrollment.getEnrollmentType(),
            enrollment.getStatus(),
            enrollment.getPrice(),
            enrollment.getCreatedAt(),
            enrollment.getExpiresAt(),
            qualification,
            available,
            enrollment.isRenewable(),
            enrollment.getPic()
        );
    }

    /** 复用选课资格，正式发布小节和媒资绑定须同时属于请求课程。 */
    @Override
    @Transactional(readOnly = true)
    public CoursePlaybackGrantDto playbackGrant(long courseId, long lessonId) {
        CurrentUser.requireRole("student");
        if (
            !QUALIFICATION_ALLOWED.equals(enrollment(courseId).getQualification())
        ) throw new ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "尚未获得学习资格、资格已过期或课程已下架"
        );
        return publishedLesson(courseId, lessonId, false);
    }

    @Override
    @Transactional(readOnly = true)
    public CoursePlaybackGrantDto trialGrant(long courseId, long lessonId) {
        return publishedLesson(courseId, lessonId, true);
    }

    /** 两种授权共用正式目录和媒资归属校验；试学不能读取可变草稿。 */
    private CoursePlaybackGrantDto publishedLesson(long courseId, long lessonId, boolean trial) {
        List<String> payloads = learningCourseMapper.selectCoursePayload(
            courseId,
            COURSE_PUBLISHED
        );
        if (payloads.isEmpty()) throw new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "课程未发布或已下架"
        );
        JsonNode course = json.readTree(payloads.getFirst());
        JsonNode chapters = json.readTree(course.path("teachplan").asText("[]"));
        for (JsonNode chapter : chapters) {
            for (JsonNode lesson : chapter.path("teachPlanTreeNodes")) {
                if (
                    lesson.path("id").asLong() != lessonId ||
                    lesson.path("grade").asInt() != 2 ||
                    lesson.path("courseId").asLong() != courseId
                ) continue;
                if (
                    trial && !"1".equals(lesson.path("isPreview").asText())
                ) throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "该小节未开放试学，请选课后学习"
                );
                JsonNode binding = lesson.path("teachplanMedia");
                String mediaId = binding.path("mediaId").asText("");
                if (
                    mediaId.isBlank() ||
                    binding.path("courseId").asLong() != courseId ||
                    binding.path("teachplanId").asLong() != lessonId ||
                    course.path("companyId").asLong() <= 0
                ) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "小节没有有效的发布视频");
                return new CoursePlaybackGrantDto(
                    courseId,
                    lessonId,
                    mediaId,
                    course.path("companyId").asLong()
                );
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "小节不属于该课程发布目录");
    }

    @Override
    public CourseOrderSnapshotDto orderSnapshot(long courseId) {
        CurrentUser.requireRole("student");
        List<Map<String, Object>> rows = learningCourseMapper.selectPendingSnapshot(
            CurrentUser.jwt().getSubject(),
            courseId
        );
        if (rows.isEmpty()) throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "课程尚未选课、已开通或已下架"
        );
        Map<String, Object> row = rows.getFirst();
        return new CourseOrderSnapshotDto(
            CurrentUser.jwt().getSubject(),
            courseId,
            row.get("course_name").toString(),
            (BigDecimal) row.get("price"),
            row.get("valid_days") == null ? null : ((Number) row.get("valid_days")).intValue()
        );
    }

    /** 先检查原选课快照，再同事务更新资格和去重记录；重复消息不延长有效期。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void payment(PaymentSuccessEvent event) {
        if (
            !"learning".equals(learningCourseMapper.selectDatabase())
        ) throw new IllegalStateException("支付资格只能写入learning库");
        CourseOrderSnapshotDto snapshot = event.getSnapshot();
        long orderId = Long.parseLong(event.getOrderId());
        if (
            orderId <= 0 ||
            snapshot == null ||
            snapshot.getUserId() == null ||
            snapshot.getUserId().isBlank() ||
            snapshot.getCourseId() <= 0 ||
            snapshot.getPrice() == null ||
            snapshot.getPrice().signum() <= 0 ||
            event.getPaidAt() == null
        ) throw new IllegalArgumentException("支付事件无效");
        String payload = json.writeValueAsString(event);
        List<String> existing = learningCourseMapper.selectProcessedPayload(orderId);
        if (!existing.isEmpty()) {
            if (
                !json.readTree(existing.getFirst()).equals(json.readTree(payload))
            ) throw new IllegalArgumentException("同一订单支付快照不一致");
            return;
        }
        List<Map<String, Object>> rows = learningCourseMapper.selectEnrollmentForUpdate(
            snapshot.getUserId(),
            snapshot.getCourseId()
        );
        if (rows.isEmpty()) throw new IllegalArgumentException("缺失选课记录");
        Map<String, Object> row = rows.getFirst();
        Integer days =
            row.get("valid_days") == null ? null : ((Number) row.get("valid_days")).intValue();
        if (
            !"70102".equals(row.get("enrollment_type")) ||
            ((BigDecimal) row.get("price")).compareTo(snapshot.getPrice()) != 0 ||
            !Objects.equals(days, snapshot.getValidDays()) ||
            !row.get("course_name").equals(snapshot.getCourseName())
        ) throw new IllegalArgumentException("支付与原选课快照不一致");
        // 同一选课记录的行锁同时保护不同订单；并发重复消息进入锁后再次检查去重。
        existing = learningCourseMapper.selectProcessedPayloadForUpdate(orderId);
        if (!existing.isEmpty()) {
            if (
                !json.readTree(existing.getFirst()).equals(json.readTree(payload))
            ) throw new IllegalArgumentException("重复支付快照冲突");
            return;
        }
        LocalDateTime expires = days != null && days > 0 ? event.getPaidAt().plusDays(days) : null;
        if (expires != null && expires.getYear() > 9999) throw new IllegalArgumentException(
            "有效期超出数据库范围"
        );
        if ("70202".equals(row.get("status"))) learningCourseMapper.activateEnrollment(
            expires,
            snapshot.getUserId(),
            snapshot.getCourseId()
        );
        learningCourseMapper.insertProcessedPayment(
            orderId,
            snapshot.getUserId(),
            snapshot.getCourseId(),
            payload
        );
    }
}
