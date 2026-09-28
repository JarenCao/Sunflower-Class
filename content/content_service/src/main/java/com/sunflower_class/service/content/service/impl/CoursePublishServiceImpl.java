package com.sunflower_class.service.content.service.impl;

import static com.sunflower_class.base.model.BusinessCodes.*;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sunflower_class.base.exception.GlobalException;
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
import com.sunflower_class.service.content.service.CourseBaseInfoService;
import com.sunflower_class.service.content.service.CoursePublishService;
import com.sunflower_class.service.content.service.TeachPlanService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 维护审核快照及正式发布快照；发布消息持久化入口目前仍待实现。
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

    /**
     * 校验预发布快照的机构归属和审核通过状态，保存正式快照、更新发布状态并删除预发布记录。
     */
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
        if (
            !COURSE_DRAFT.equals(courseBase.getStatus()) &&
            !COURSE_OFFLINE.equals(courseBase.getStatus())
        ) {
            GlobalException.cast("只有未发布或已下架的课程可以发布");
        }
        if (!AUDIT_APPROVED.equals(courseBase.getAuditStatus())) {
            GlobalException.cast("课程尚未审核通过");
        }

        CoursePublish existingPublish = coursePublishMapper.selectById(courseId);
        CoursePublishPre coursePublishPre = coursePublishPreMapper.selectById(courseId);
        if (existingPublish != null && COURSE_PUBLISHED.equals(existingPublish.getStatus())) {
            GlobalException.cast("课程已发布，不能重复发布");
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

    /**
     * 课程发布消息的预留入口，目前为空实现；调用此方法不会保存消息或触发搜索、缓存同步。
     */
    private void saveCoursePublishMessage(Long coureseId) {}

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
        log.info("课程下架完成，课程ID：{}，机构ID：{}", courseId, companyId);
    }
}
