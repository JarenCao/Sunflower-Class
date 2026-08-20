package com.sunflower_class.service.content.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson.JSON;
import com.sunflower_class.base.exception.GlobalException;
import com.sunflower_class.model.dto.CourseBaseInfoDto;
import com.sunflower_class.model.dto.TeachPlanDto;
import com.sunflower_class.model.po.CourseBase;
import com.sunflower_class.model.po.CourseMarket;
import com.sunflower_class.model.po.CoursePublish;
import com.sunflower_class.model.po.CoursePublishPre;
import com.sunflower_class.service.content.mapper.CourseBaseMapper;
import com.sunflower_class.service.content.mapper.CourseMarketMapper;
import com.sunflower_class.service.content.mapper.CoursePublishMapper;
import com.sunflower_class.service.content.mapper.CoursePublishPreMapper;
import com.sunflower_class.service.content.service.CourseBaseInfoService;
import com.sunflower_class.service.content.service.CoursePublishService;
import com.sunflower_class.service.content.service.TeachPlanService;

import lombok.extern.slf4j.Slf4j;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void commitAudit(Long companyId, Long courseId) {
        log.info("开始提交课程审核, courseId={}, companyId={}", courseId, companyId);

        CourseBase courseBase = courseBaseMapper.selectById(courseId);
        if (courseBase == null) {
            log.warn("课程不存在, courseId={}", courseId);
            GlobalException.cast("课程不存在，请检查课程ID是否正确");
        }
        log.debug("查询到课程基础信息, courseId={}, courseName={}, auditStatus={}", courseId, courseBase.getName(),
                courseBase.getAuditStatus());

        if (!courseBase.getCompanyId().equals(companyId)) {
            log.warn("机构权限校验失败, courseId={}, courseCompanyId={}, requestCompanyId={}", courseId,
                    courseBase.getCompanyId(), companyId);
            GlobalException.cast("无权限操作该课程，只能修改本机构的课程信息");
        }

        if ("30403".equals(courseBase.getAuditStatus())) {
            log.warn("课程审核状态不允许提交, courseId={}, currentStatus={}", courseId, courseBase.getAuditStatus());
            GlobalException.cast("课程当前状态不允许提交审核，请等待审核结束或联系管理员");
        }

        List<TeachPlanDto> teachPlanTree = teachPlanService.findTeachPlanTree(courseId);
        if (teachPlanTree == null || teachPlanTree.isEmpty()) {
            log.warn("教学计划为空, courseId={}", courseId);
            GlobalException.cast("课程缺少教学计划，请先添加教学计划后再提交审核");
        }
        log.debug("查询到教学计划, courseId={}, teachPlanCount={}", courseId,
                teachPlanTree != null ? teachPlanTree.size() : 0);

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
        coursePublishPre.setStatus("30403");

        CoursePublishPre result = coursePublishPreMapper.selectById(courseId);
        if (result == null) {
            coursePublishPreMapper.insert(coursePublishPre);
            log.info("插入课程预发布记录成功, courseId={}", courseId);
        } else {
            coursePublishPreMapper.updateById(coursePublishPre);
            log.info("更新课程预发布记录成功, courseId={}", courseId);
        }

        courseBase.setStatus("30403");
        courseBaseMapper.updateById(courseBase);
        log.info("课程提交审核完成, courseId={}, companyId={}, status=30403", courseId, companyId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishCourse(Long companyId, Long courseId) {
        log.info("开始发布课程, courseId={}, companyId={}", courseId, companyId);

        CoursePublishPre coursePublishPre = coursePublishPreMapper.selectById(courseId);
        if (coursePublishPre == null) {
            log.warn("课程发布预信息不存在, courseId={}", courseId);
            GlobalException.cast("课程发布信息不存在");
        }

        if (!coursePublishPre.getCompanyId().equals(companyId)) {
            log.warn("机构权限校验失败, courseId={}, courseCompanyId={}, requestCompanyId={}",
                    courseId, coursePublishPre.getCompanyId(), companyId);
            GlobalException.cast("无权限操作该课程，只能修改本机构的课程信息");
        }

        if (!"30404".equals(coursePublishPre.getStatus())) {
            log.warn("课程未通过审核, courseId={}, currentStatus={}", courseId, coursePublishPre.getStatus());
            GlobalException.cast("该课程未通过审核");
        }

        CoursePublish coursePublish = new CoursePublish();
        BeanUtils.copyProperties(coursePublishPre, coursePublish);
        coursePublish.setStatus("30404");

        CoursePublish existingPublish = coursePublishMapper.selectById(courseId);
        if (existingPublish == null) {

            coursePublishMapper.insert(coursePublish);
            log.info("新增课程发布记录成功, courseId={}", courseId);
        } else {

            coursePublishMapper.updateById(coursePublish);
            log.info("更新课程发布记录成功, courseId={}", courseId);
        }

        CourseBase courseBase = courseBaseMapper.selectById(courseId);
        if (courseBase == null) {
            log.error("课程基础信息不存在, courseId={}", courseId);
            GlobalException.cast("课程信息异常，请稍后重试");
        }
        courseBase.setAuditStatus("30404");
        courseBaseMapper.updateById(courseBase);
        log.debug("更新课程审核状态完成, courseId={}, status=30404", courseId);

        coursePublishPreMapper.deleteById(courseId);
        log.info("课程发布完成, courseId={}, companyId={}", courseId, companyId);
    }

}