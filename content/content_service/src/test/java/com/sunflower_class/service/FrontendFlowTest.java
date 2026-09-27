package com.sunflower_class.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sunflower_class.base.exception.GlobalException;
import com.sunflower_class.model.dto.*;
import com.sunflower_class.model.po.*;
import com.sunflower_class.service.content.mapper.*;
import com.sunflower_class.service.content.service.*;
import com.sunflower_class.service.content.service.impl.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 前端联调所依赖的教学计划、提交审核与发布状态转换回归测试。
 */
@ExtendWith(MockitoExtension.class)
class FrontendFlowTest {

    @Mock
    TeachplanMapper teachplanMapper;

    @Mock
    CourseBaseMapper courseBaseMapper;

    @Mock
    CourseMarketMapper courseMarketMapper;

    @Mock
    CoursePublishPreMapper coursePublishPreMapper;

    @Mock
    CoursePublishMapper coursePublishMapper;

    @Mock
    CourseBaseInfoService courseBaseInfoService;

    @Mock
    TeachPlanService teachPlanService;

    @InjectMocks
    TeachPlanServiceImpl plans;

    @InjectMocks
    CoursePublishServiceImpl publication;

    /**
     * 验证教学计划父节点及层级正确地从请求映射到数据库实体。
     */
    @Test
    void savesParentAndShortGradeFromRequest() {
        var request = new AddTeachPlanDto();
        request.setCourseId(20L);
        request.setParentId(30L);
        request.setGrade(2);
        request.setPname("小节");
        plans.saveTeachPlan(request);
        var saved = ArgumentCaptor.forClass(Teachplan.class);
        verify(teachplanMapper).insert(saved.capture());
        assertEquals(30L, saved.getValue().getParentid());
        assertEquals((short) 2, saved.getValue().getGrade());
        assertEquals(10101, saved.getValue().getStatus());
    }

    /**
     * 验证提交审核只更新审核状态，保留原有发布状态。
     */
    @Test
    void submittingAuditKeepsPublicationState() {
        var course = new CourseBase();
        course.setId(20L);
        course.setCompanyId(10L);
        course.setAuditStatus("30402");
        course.setStatus("30501");
        when(courseBaseMapper.selectById(20L)).thenReturn(course);
        when(teachPlanService.findTeachPlanTree(20L)).thenReturn(List.of(new TeachPlanDto()));
        when(courseMarketMapper.selectById(20L)).thenReturn(new CourseMarket());
        var dto = new CourseBaseInfoDto();
        dto.setId(20L);
        when(courseBaseInfoService.getCourseById(20L)).thenReturn(dto);
        publication.commitAudit(10L, 20L);
        assertEquals("30403", course.getAuditStatus());
        assertEquals("30501", course.getStatus());
        verify(courseBaseMapper).updateById(course);
    }

    /**
     * 验证审核未通过的预发布记录不能发布。
     */
    @Test
    void rejectsUnapprovedPublication() {
        var pending = new CoursePublishPre();
        pending.setCompanyId(10L);
        pending.setStatus("30403");
        when(coursePublishPreMapper.selectById(20L)).thenReturn(pending);
        assertThrows(GlobalException.class, () -> publication.publishCourse(10L, 20L));
        verifyNoInteractions(coursePublishMapper);
    }

    /**
     * 验证审核通过后保存发布快照，并将课程标记为已发布。
     */
    @Test
    void approvedPublicationStoresSnapshotAndPublishedState() {
        var approved = new CoursePublishPre();
        approved.setId(20L);
        approved.setCompanyId(10L);
        approved.setStatus("30404");
        var course = new CourseBase();
        course.setId(20L);
        course.setCompanyId(10L);
        course.setStatus("30501");
        when(coursePublishPreMapper.selectById(20L)).thenReturn(approved);
        when(courseBaseMapper.selectById(20L)).thenReturn(course);
        publication.publishCourse(10L, 20L);
        var snapshot = ArgumentCaptor.forClass(CoursePublish.class);
        verify(coursePublishMapper).insert(snapshot.capture());
        assertEquals("30502", snapshot.getValue().getStatus());
        assertNotNull(snapshot.getValue().getOnlineDate());
        assertEquals("30502", course.getStatus());
        verify(coursePublishPreMapper).deleteById(20L);
    }
}
