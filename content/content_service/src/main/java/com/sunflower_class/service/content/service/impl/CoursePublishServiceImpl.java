package com.sunflower_class.service.content.service.impl;

import static com.sunflower_class.base.model.BusinessCodes.AUDIT_APPROVED;
import static com.sunflower_class.base.model.BusinessCodes.AUDIT_DRAFT;
import static com.sunflower_class.base.model.BusinessCodes.AUDIT_PENDING;
import static com.sunflower_class.base.model.BusinessCodes.AUDIT_REJECTED;
import static com.sunflower_class.base.model.BusinessCodes.CHARGE_FREE;
import static com.sunflower_class.base.model.BusinessCodes.CHARGE_PAID;
import static com.sunflower_class.base.model.BusinessCodes.COURSE_DRAFT;
import static com.sunflower_class.base.model.BusinessCodes.COURSE_OFFLINE;
import static com.sunflower_class.base.model.BusinessCodes.COURSE_PUBLISHED;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunflower_class.base.exception.GlobalException;
import com.sunflower_class.base.model.PageParams;
import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.base.security.CurrentUser;
import com.sunflower_class.model.dto.CourseBaseInfoDto;
import com.sunflower_class.model.dto.TeachPlanDto;
import com.sunflower_class.model.po.CourseBase;
import com.sunflower_class.model.po.CourseMarket;
import com.sunflower_class.model.po.CoursePublish;
import com.sunflower_class.model.po.CoursePublishPre;
import com.sunflower_class.model.po.MqMessage;
import com.sunflower_class.service.content.mapper.CourseBaseMapper;
import com.sunflower_class.service.content.mapper.CourseMarketMapper;
import com.sunflower_class.service.content.mapper.CoursePublishMapper;
import com.sunflower_class.service.content.mapper.CoursePublishPreMapper;
import com.sunflower_class.service.content.mapper.MqMessageMapper;
import com.sunflower_class.service.content.service.AssociationMediaService;
import com.sunflower_class.service.content.service.CourseBaseInfoService;
import com.sunflower_class.service.content.service.CoursePublishService;
import com.sunflower_class.service.content.service.TeachPlanService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 维护审核快照和正式发布快照；正式发布时在同一事务保存待发送消息。
 */
@Slf4j
@Service
public class CoursePublishServiceImpl implements CoursePublishService {

    @Autowired
    private CourseBaseMapper courseBaseMapper;

    @Autowired
    private CourseBaseInfoService courseBaseInfoService;

    @Autowired
    private TeachPlanService teachPlanService;

    @Autowired
    private CourseMarketMapper courseMarketMapper;

    @Autowired
    private CoursePublishPreMapper coursePublishPreMapper;

    @Autowired
    private CoursePublishMapper coursePublishMapper;

    @Autowired
    private MqMessageMapper mqMessageMapper;

    @Autowired
    private AssociationMediaService associationMediaService;

    /**
     * 校验机构归属、当前审核状态、教学计划及营销信息，生成待审核快照并更新审核状态。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void commitAudit(Long companyId, Long courseId) {
        log.info("开始提交课程审核, courseId={}, companyId={}", courseId, companyId);

        if (companyId == null || courseId == null || courseId <= 0) {
            GlobalException.cast("课程或机构编号无效");
        }
        // 锁定课程，避免提审与编辑、下架等状态修改交错提交。
        CourseBase courseBase = courseBaseMapper.selectOne(
            new LambdaQueryWrapper<CourseBase>().eq(CourseBase::getId, courseId).last("FOR UPDATE")
        );
        if (courseBase == null) {
            log.warn("课程不存在, courseId={}", courseId);
            GlobalException.cast("课程不存在，请检查课程ID是否正确");
        }
        log.debug(
            "查询到课程基础信息, courseId={}, courseName={}, auditStatus={}",
            courseId,
            courseBase.getName(),
            courseBase.getAuditStatus()
        );

        if (!courseBase.getCompanyId().equals(companyId)) {
            log.warn(
                "机构权限校验失败, courseId={}, courseCompanyId={}, requestCompanyId={}",
                courseId,
                courseBase.getCompanyId(),
                companyId
            );
            GlobalException.cast("无权限操作该课程，只能修改本机构的课程信息");
        }

        if (
            !COURSE_DRAFT.equals(courseBase.getStatus()) &&
            !COURSE_OFFLINE.equals(courseBase.getStatus())
        ) {
            GlobalException.cast("已发布或状态异常的课程不能提交审核");
        }
        if (
            !AUDIT_DRAFT.equals(courseBase.getAuditStatus()) &&
            !AUDIT_REJECTED.equals(courseBase.getAuditStatus())
        ) {
            log.warn(
                "课程审核状态不允许提交, courseId={}, currentStatus={}",
                courseId,
                courseBase.getAuditStatus()
            );
            GlobalException.cast("只有未提交或审核驳回的课程可以提交审核");
        }

        CoursePublishPre existingPre = coursePublishPreMapper.selectById(courseId);
        if (
            existingPre != null &&
            (AUDIT_PENDING.equals(existingPre.getStatus()) ||
                AUDIT_APPROVED.equals(existingPre.getStatus()))
        ) {
            GlobalException.cast("课程预发布审核状态与基础信息不一致，请先核对");
        }

        List<TeachPlanDto> teachPlanTree = teachPlanService.findTeachPlanTree(courseId);
        if (teachPlanTree == null || teachPlanTree.isEmpty()) {
            log.warn("教学计划为空, courseId={}", courseId);
            GlobalException.cast("课程缺少教学计划，请先添加教学计划后再提交审核");
        }
        log.debug(
            "查询到教学计划, courseId={}, teachPlanCount={}",
            courseId,
            teachPlanTree != null ? teachPlanTree.size() : 0
        );

        CourseMarket courseMarket = courseMarketMapper.selectById(courseId);
        if (courseMarket == null) {
            log.warn("课程营销信息为空, courseId={}", courseId);
            GlobalException.cast("课程营销信息不能为空，请先完善营销信息");
        }
        log.debug("查询到课程营销信息, courseId={}", courseId);

        CoursePublishPre coursePublishPre = new CoursePublishPre();
        CourseBaseInfoDto courseBaseInfo = courseBaseInfoService.getCourseById(courseId);
        BeanUtils.copyProperties(courseBaseInfo, coursePublishPre);

        String teachPlanTreeJson = JSON.toJSONString(teachPlanTree);
        coursePublishPre.setTeachplan(teachPlanTreeJson);

        String courseMarketJson = JSON.toJSONString(courseMarket);
        coursePublishPre.setMarket(courseMarketJson);

        // 师资在提审时冻结，平台管理员审核提交快照，学员读取正式发布快照。
        coursePublishPre.setTeachers(
            JSON.toJSONString(courseBaseInfoService.listCourseTeachers(courseId))
        );
        coursePublishPre.setCompanyId(companyId);
        coursePublishPre.setCreateDate(LocalDateTime.now());
        coursePublishPre.setStatus(AUDIT_PENDING);

        if (existingPre == null) {
            coursePublishPreMapper.insert(coursePublishPre);
            log.info("插入课程预发布记录成功, courseId={}", courseId);
        } else {
            coursePublishPreMapper.updateById(coursePublishPre);
            log.info("更新课程预发布记录成功, courseId={}", courseId);
        }

        courseBase.setAuditStatus(AUDIT_PENDING);
        if (courseBaseMapper.updateById(courseBase) != 1) {
            GlobalException.cast("课程审核状态更新失败");
        }
        log.info(
            "课程提交审核完成, courseId={}, companyId={}, auditStatus={}",
            courseId,
            companyId,
            AUDIT_PENDING
        );
    }

    /** 锁定课程并核对待审快照，同一事务保存审核结论与操作记录。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewCourse(Long courseId, boolean approved, String reason) {
        CurrentUser.requireRole("admin");
        String reviewer = CurrentUser.jwt().getClaimAsString("name");
        if (courseId == null || courseId <= 0) {
            GlobalException.cast("课程或机构编号无效");
        }
        String opinion = reason == null ? "" : reason.trim();
        String operator = reviewer == null ? "" : reviewer.trim();
        if (!approved && opinion.isBlank()) {
            GlobalException.cast("驳回课程时必须填写原因");
        }
        if (opinion.length() > 255 || operator.isBlank() || operator.length() > 50) {
            GlobalException.cast("审核意见或审核人长度无效");
        }

        CourseBase course = courseBaseMapper.selectOne(
            new LambdaQueryWrapper<CourseBase>().eq(CourseBase::getId, courseId).last("FOR UPDATE")
        );
        if (course == null) GlobalException.cast("课程不存在");
        if (!AUDIT_PENDING.equals(course.getAuditStatus())) {
            GlobalException.cast("只有审核中的课程可以审核");
        }
        if (
            !COURSE_DRAFT.equals(course.getStatus()) && !COURSE_OFFLINE.equals(course.getStatus())
        ) {
            GlobalException.cast("已发布课程不能重新审核");
        }

        CoursePublishPre pending = coursePublishPreMapper.selectById(courseId);
        if (
            pending == null ||
            !Objects.equals(course.getCompanyId(), pending.getCompanyId()) ||
            !AUDIT_PENDING.equals(pending.getStatus())
        ) {
            GlobalException.cast("课程待审快照不存在或状态不一致");
        }

        String result = approved ? AUDIT_APPROVED : AUDIT_REJECTED;
        LocalDateTime reviewedAt = LocalDateTime.now();
        pending.setStatus(result);
        pending.setAuditDate(reviewedAt);
        pending.setRemark(opinion.isBlank() ? null : opinion);
        if (coursePublishPreMapper.updateById(pending) != 1) {
            GlobalException.cast("课程审核快照更新失败");
        }
        course.setAuditStatus(result);
        course.setChangeDate(reviewedAt);
        if (courseBaseMapper.updateById(course) != 1) {
            GlobalException.cast("课程审核状态更新失败");
        }
        if (
            courseBaseMapper.insertCourseAudit(courseId, opinion, result, operator, reviewedAt) != 1
        ) {
            GlobalException.cast("课程审核记录保存失败");
        }
        log.info("课程审核完成，课程ID：{}，审核状态：{}，审核人：{}", courseId, result, operator);
    }

    /** 仅允许读取配置机构所属课程的审核历史。 */
    @Override
    public List<Map<String, Object>> auditHistory(Long companyId, Long courseId) {
        boolean reviewer = "admin".equals(CurrentUser.jwt().getClaimAsString("role"));
        if (!reviewer) companyId = CurrentUser.companyId();
        if (courseId == null || courseId <= 0) {
            GlobalException.cast("课程或机构编号无效");
        }
        CourseBase course = courseBaseMapper.selectById(courseId);
        if (course == null || (!reviewer && !companyId.equals(course.getCompanyId()))) {
            GlobalException.cast("课程不存在或无权限查看审核记录");
        }
        return courseBaseMapper.selectCourseAuditHistory(courseId);
    }

    /** 审核队列不限制机构；复用现有实体、分页和营销数据。 */
    @Override
    public PageResult<CourseBaseInfoDto> auditQueue(PageParams params, String status) {
        CurrentUser.requireRole("admin");
        if (!Set.of(AUDIT_PENDING, AUDIT_APPROVED, AUDIT_REJECTED).contains(status)) {
            GlobalException.cast("审核状态无效");
        }
        Page<CourseBase> page = courseBaseMapper.selectPage(
            new Page<CourseBase>(
                Math.max(1, params.getPageNo()),
                Math.min(100, Math.max(1, params.getPageSize()))
            ),
            new LambdaQueryWrapper<CourseBase>()
                .eq(CourseBase::getAuditStatus, status)
                // 旧数据可能只标记审核中而没有提交记录；队列只提供可读取的完整快照。
                .and(query ->
                    query
                        .exists(
                            "SELECT 1 FROM course_publish_pre p WHERE p.id=course_base.id AND p.company_id=course_base.company_id AND p.status=course_base.audit_status"
                        )
                        .or()
                        .exists(
                            "SELECT 1 FROM course_publish p WHERE p.id=course_base.id AND p.company_id=course_base.company_id AND course_base.audit_status='30404'"
                        )
                )
                .orderByDesc(CourseBase::getChangeDate)
        );
        List<CourseBaseInfoDto> items = page
            .getRecords()
            .stream()
            .map(course -> {
                CourseBaseInfoDto dto = new CourseBaseInfoDto();
                BeanUtils.copyProperties(course, dto);
                CourseMarket market = courseMarketMapper.selectById(course.getId());
                if (market != null) BeanUtils.copyProperties(market, dto);
                return dto;
            })
            .toList();
        return new PageResult<>(items, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 审核读取提交快照，已发布课程复用正式快照，不暴露机构编辑能力。 */
    @Override
    public CoursePublishPre auditDetail(Long courseId) {
        CurrentUser.requireRole("admin");
        CoursePublishPre snapshot = coursePublishPreMapper.selectById(courseId);
        if (snapshot != null) return snapshot;
        CoursePublish published = coursePublishMapper.selectById(courseId);
        if (published == null) throw new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "课程审核快照不存在"
        );
        snapshot = new CoursePublishPre();
        BeanUtils.copyProperties(published, snapshot);
        snapshot.setStatus(AUDIT_APPROVED);
        return snapshot;
    }

    /** 校验课程及审核快照后发布；已成功发布的相同请求直接返回，不重复写入记录。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishCourse(Long companyId, Long courseId) {
        log.info("开始发布课程, courseId={}, companyId={}", courseId, companyId);

        if (companyId == null || courseId == null || courseId <= 0) {
            GlobalException.cast("课程或机构编号无效");
        }
        CourseBase courseBase = courseBaseMapper.selectOne(
            new LambdaQueryWrapper<CourseBase>().eq(CourseBase::getId, courseId).last("FOR UPDATE")
        );
        if (courseBase == null) GlobalException.cast("课程不存在");
        if (!companyId.equals(courseBase.getCompanyId())) {
            GlobalException.cast("不能发布非本机构课程");
        }

        CoursePublish existingPublish = coursePublishMapper.selectById(courseId);
        if (existingPublish != null && !companyId.equals(existingPublish.getCompanyId())) {
            GlobalException.cast("课程发布记录不属于当前机构");
        }
        // 课程和正式快照均为已发布时属于重试请求，不能再次插入或更新时间。
        if (COURSE_PUBLISHED.equals(courseBase.getStatus())) {
            if (
                existingPublish == null ||
                !COURSE_PUBLISHED.equals(existingPublish.getStatus()) ||
                !AUDIT_APPROVED.equals(courseBase.getAuditStatus())
            ) {
                GlobalException.cast("课程发布状态与正式记录不一致");
            }
            log.info("课程已发布，跳过重复请求, courseId={}", courseId);
            return;
        }
        if (
            !COURSE_DRAFT.equals(courseBase.getStatus()) &&
            !COURSE_OFFLINE.equals(courseBase.getStatus())
        ) {
            GlobalException.cast("只有未发布或已下架的课程可以发布");
        }
        if (!AUDIT_APPROVED.equals(courseBase.getAuditStatus())) {
            GlobalException.cast("课程尚未审核通过");
        }

        CoursePublishPre coursePublishPre = coursePublishPreMapper.selectById(courseId);
        if (existingPublish != null && COURSE_PUBLISHED.equals(existingPublish.getStatus())) {
            GlobalException.cast("课程发布状态与正式记录不一致");
        }

        // 下架后未改动内容时，原正式快照仍有效，可直接重新上架。
        if (
            coursePublishPre == null &&
            COURSE_OFFLINE.equals(courseBase.getStatus()) &&
            existingPublish != null &&
            COURSE_OFFLINE.equals(existingPublish.getStatus())
        ) {
            if (!companyId.equals(existingPublish.getCompanyId())) {
                GlobalException.cast("不能发布非本机构课程");
            }
            validatePublishContent(courseBase);
            existingPublish.setStatus(COURSE_PUBLISHED);
            existingPublish.setOnlineDate(LocalDateTime.now());
            existingPublish.setOfflineDate(null);
            if (coursePublishMapper.updateById(existingPublish) != 1) {
                GlobalException.cast("课程重新上架失败");
            }
            courseBase.setStatus(COURSE_PUBLISHED);
            if (courseBaseMapper.updateById(courseBase) != 1) {
                GlobalException.cast("课程重新上架失败");
            }
            saveCoursePublishMessage(courseId);
            log.info("课程重新上架完成, courseId={}, companyId={}", courseId, companyId);
            return;
        }

        if (coursePublishPre == null) {
            log.warn("课程发布预信息不存在, courseId={}", courseId);
            GlobalException.cast("课程发布信息不存在");
        }

        if (!companyId.equals(coursePublishPre.getCompanyId())) {
            log.warn(
                "机构权限校验失败, courseId={}, courseCompanyId={}, requestCompanyId={}",
                courseId,
                coursePublishPre.getCompanyId(),
                companyId
            );
            GlobalException.cast("无权限操作该课程，只能修改本机构的课程信息");
        }

        if (!AUDIT_APPROVED.equals(coursePublishPre.getStatus())) {
            log.warn(
                "课程未通过审核, courseId={}, currentStatus={}",
                courseId,
                coursePublishPre.getStatus()
            );
            GlobalException.cast("该课程未通过审核");
        }

        validatePublishContent(courseBase);

        CoursePublish coursePublish = new CoursePublish();
        BeanUtils.copyProperties(coursePublishPre, coursePublish);
        coursePublish.setStatus(COURSE_PUBLISHED);
        coursePublish.setOnlineDate(LocalDateTime.now());

        if (existingPublish == null) {
            coursePublishMapper.insert(coursePublish);
            log.info("新增课程发布记录成功, courseId={}", courseId);
        } else {
            coursePublishMapper.updateById(coursePublish);
            log.info("更新课程发布记录成功, courseId={}", courseId);
        }

        // 审核结论必须由审核流程写入，这里仅更新发布状态，不能自行批准课程。
        courseBase.setStatus(COURSE_PUBLISHED);
        if (courseBaseMapper.updateById(courseBase) != 1) {
            GlobalException.cast("课程发布状态更新失败");
        }
        log.debug("更新课程发布状态完成, courseId={}, status={}", courseId, COURSE_PUBLISHED);

        saveCoursePublishMessage(courseId);

        coursePublishPreMapper.deleteById(courseId);
        log.info("课程发布完成, courseId={}, companyId={}", courseId, companyId);
    }

    /** 按当前数据库内容核对必填字段、营销记录、教学目录及小节媒资，避免发布失效快照。 */
    private void validatePublishContent(CourseBase course) {
        if (
            course.getName() == null ||
            course.getName().isBlank() ||
            course.getMt() == null ||
            course.getMt().isBlank() ||
            course.getSt() == null ||
            course.getSt().isBlank() ||
            course.getGrade() == null ||
            course.getGrade().isBlank() ||
            course.getTeachmode() == null ||
            course.getTeachmode().isBlank() ||
            course.getUsers() == null ||
            course.getUsers().isBlank() ||
            course.getPic() == null ||
            course.getPic().isBlank()
        ) {
            GlobalException.cast("课程基础信息不完整，请先完善课程信息");
        }

        CourseMarket market = courseMarketMapper.selectById(course.getId());
        if (
            market == null ||
            market.getCharge() == null ||
            !Set.of(CHARGE_FREE, CHARGE_PAID).contains(market.getCharge())
        ) {
            GlobalException.cast("课程营销信息不存在或收费类型无效");
        }
        if (
            CHARGE_PAID.equals(market.getCharge()) &&
            (market.getPrice() == null || market.getPrice().compareTo(BigDecimal.ZERO) <= 0)
        ) {
            GlobalException.cast("收费课程价格必须大于0");
        }

        List<TeachPlanDto> chapters = teachPlanService.findTeachPlanTree(course.getId());
        if (chapters == null || chapters.isEmpty()) {
            GlobalException.cast("课程缺少教学计划");
        }
        boolean hasLesson = false;
        Set<String> checkedMedia = new HashSet<>();
        for (TeachPlanDto chapter : chapters) {
            if (chapter.getTeachPlanTreeNodes() == null) continue;
            for (TeachPlanDto lesson : chapter.getTeachPlanTreeNodes()) {
                if (lesson.getId() == null) continue;
                hasLesson = true;
                String mediaId =
                    lesson.getTeachplanMedia() == null
                        ? null
                        : lesson.getTeachplanMedia().getMediaId();
                // 视频小节必须绑定媒资；已有绑定的其他小节也不能引用失效文件。
                if ((mediaId == null || mediaId.isBlank()) && "1".equals(lesson.getMediaType())) {
                    GlobalException.cast("视频小节“" + lesson.getPname() + "”未关联媒资");
                }
                if (mediaId != null && !mediaId.isBlank() && checkedMedia.add(mediaId)) {
                    associationMediaService.requireReadyMedia(mediaId);
                }
            }
        }
        if (!hasLesson) GlobalException.cast("教学计划缺少小节");
    }

    /** 将发布事件写入待发送表；调用方的事务保证快照、状态和消息同时提交。 */
    private void saveCoursePublishMessage(Long courseId) {
        CoursePublish published = coursePublishMapper.selectById(courseId);
        if (
            published == null ||
            (!COURSE_PUBLISHED.equals(published.getStatus()) &&
                !COURSE_OFFLINE.equals(published.getStatus()))
        ) {
            GlobalException.cast("课程发布记录不存在，无法保存发布消息");
        }

        MqMessage message = new MqMessage();
        message.setMessageType("course_publish");
        message.setBusinessKey1(courseId.toString());
        message.setBusinessKey2(published.getCompanyId().toString());
        message.setBusinessKey3(published.getStatus());
        // 保存事件产生时的快照，避免延迟消费把新版本误当作旧事件。
        message.setPayload(JSON.toJSONString(published));
        message.setExecuteNum(0);
        message.setState("0");
        // 各处理阶段均未开始；消息发送及消费由开发清单的下一项负责。
        message.setStageState1("0");
        message.setStageState2("0");
        message.setStageState3("0");
        message.setStageState4("0");
        if (mqMessageMapper.insert(message) != 1) {
            GlobalException.cast("课程发布消息保存失败");
        }
        log.info("课程发布消息已落库, courseId={}, messageId={}", courseId, message.getId());
    }

    /**
     * 同一事务更新基础表和公开快照；任一记录异常时保留原发布状态。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void offlineCourse(Long companyId, Long courseId) {
        if (courseId == null || courseId <= 0 || companyId == null) {
            GlobalException.cast("课程或机构编号无效");
        }

        CourseBase course = courseBaseMapper.selectOne(
            new LambdaQueryWrapper<CourseBase>().eq(CourseBase::getId, courseId).last("FOR UPDATE")
        );
        if (course == null) {
            GlobalException.cast("课程不存在");
        }
        if (!companyId.equals(course.getCompanyId())) {
            GlobalException.cast("不能下架非本机构课程");
        }
        if (!COURSE_PUBLISHED.equals(course.getStatus())) {
            GlobalException.cast("只有已发布课程可以下架");
        }

        CoursePublish published = coursePublishMapper.selectById(courseId);
        if (published == null || !COURSE_PUBLISHED.equals(published.getStatus())) {
            GlobalException.cast("课程发布记录不存在或状态不一致");
        }

        // 先撤销公开快照，再更新课程状态；事务保证二者要么同时生效，要么同时回滚。
        published.setStatus(COURSE_OFFLINE);
        published.setOfflineDate(LocalDateTime.now());
        if (coursePublishMapper.updateById(published) != 1) {
            GlobalException.cast("课程下架失败");
        }
        course.setStatus(COURSE_OFFLINE);
        course.setChangeDate(LocalDateTime.now());
        if (courseBaseMapper.updateById(course) != 1) {
            GlobalException.cast("课程下架失败");
        }
        // 下架也必须通知下游，防止搜索与学习目录继续展示已下架课程。
        saveCoursePublishMessage(courseId);
        log.info("课程下架完成，课程ID：{}，机构ID：{}", courseId, companyId);
    }

    /** 复用原发布快照查询，接口层不访问 Mapper。 */
    @Override
    public boolean isPublishedCover(String mediaId) {
        if (!mediaId.matches("[a-fA-F0-9]{32}")) return false;
        return (
            coursePublishMapper.selectCount(
                new LambdaQueryWrapper<CoursePublish>()
                    .eq(CoursePublish::getStatus, COURSE_PUBLISHED)
                    .eq(CoursePublish::getPic, "/api/media/files/" + mediaId + "/content")
            ) > 0
        );
    }

    /** 复用原发布快照查询，接口层不访问 Mapper。 */
    @Override
    public PageResult<CoursePublish> publishedCourses(long pageNo, long pageSize, String q) {
        LambdaQueryWrapper<CoursePublish> query = new LambdaQueryWrapper<CoursePublish>()
            .eq(CoursePublish::getStatus, COURSE_PUBLISHED)
            .like(!q.isBlank(), CoursePublish::getName, q)
            .orderByDesc(CoursePublish::getOnlineDate);
        Page<CoursePublish> page = coursePublishMapper.selectPage(
            new Page<>(Math.max(1, pageNo), Math.min(100, Math.max(1, pageSize))),
            query
        );
        return new PageResult<>(
            page.getRecords(),
            page.getTotal(),
            page.getCurrent(),
            page.getSize()
        );
    }

    /** 复用原发布快照查询，接口层不访问 Mapper。 */
    @Override
    public CoursePublish publishedCourse(Long id) {
        CoursePublish course = coursePublishMapper.selectById(id);
        if (course == null || !COURSE_PUBLISHED.equals(course.getStatus())) GlobalException.cast(
            "课程未发布或不存在"
        );
        return course;
    }

    /** 发布消息按机构归属筛选，返回前继续隐藏完整课程快照。 */
    @Override
    public List<MqMessage> publicationMessages(Long companyId, long courseId) {
        List<MqMessage> items = mqMessageMapper.selectList(
            new LambdaQueryWrapper<MqMessage>()
                .eq(MqMessage::getMessageType, "course_publish")
                .isNotNull(MqMessage::getPayload)
                .eq(MqMessage::getBusinessKey1, Long.toString(courseId))
                .eq(MqMessage::getBusinessKey2, companyId.toString())
                .orderByDesc(MqMessage::getId)
                .last("LIMIT 10")
        );
        items.forEach(item -> item.setPayload(null));
        return items;
    }

    /** 只恢复原机构的未完成消息，保留原重试条件。 */
    @Override
    public void retryPublicationMessage(Long companyId, long id) {
        if (mqMessageMapper.retry(id, companyId.toString()) != 1) GlobalException.cast(
            "消息已完成、不存在或不属于本机构"
        );
    }
}
