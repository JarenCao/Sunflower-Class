package com.sunflower_class.service.content.service.impl;

import static com.sunflower_class.base.model.BusinessCodes.AUDIT_APPROVED;
import static com.sunflower_class.base.model.BusinessCodes.AUDIT_DRAFT;
import static com.sunflower_class.base.model.BusinessCodes.AUDIT_PENDING;
import static com.sunflower_class.base.model.BusinessCodes.COURSE_DRAFT;
import static com.sunflower_class.base.model.BusinessCodes.COURSE_OFFLINE;
import static com.sunflower_class.base.model.BusinessCodes.PROCESS_READY;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sunflower_class.base.exception.GlobalException;
import com.sunflower_class.model.dto.BindTeachplanMediaDto;
import com.sunflower_class.model.po.CourseBase;
import com.sunflower_class.model.po.Teachplan;
import com.sunflower_class.model.po.TeachplanMedia;
import com.sunflower_class.service.content.mapper.CourseBaseMapper;
import com.sunflower_class.service.content.mapper.CoursePublishPreMapper;
import com.sunflower_class.service.content.mapper.TeachplanMapper;
import com.sunflower_class.service.content.mapper.TeachplanMediaMapper;
import com.sunflower_class.service.content.service.AssociationMediaService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * 通过服务发现核对媒资归属与可用性，再用事务替换教学计划媒资关联。
 */
@Slf4j
@Service
public class AssociationMediaServiceImpl implements AssociationMediaService {

    @Autowired
    private TeachplanMapper teachplanMapper;

    @Autowired
    private TeachplanMediaMapper teachplanMediaMapper;

    @Autowired
    private CourseBaseMapper courseBaseMapper;

    @Autowired
    private CoursePublishPreMapper coursePublishPreMapper;

    @Autowired
    private DiscoveryClient discoveryClient;

    @Value("${sunflower.company-id}")
    private Long companyId;

    /** 通过 Nacos 中的 media-api 实例读取文件元数据，服务不可用时拒绝绑定。 */
    private Map<?, ?> requireReadyMedia(String mediaId) {
        List<ServiceInstance> instances = discoveryClient.getInstances("media-api");
        if (instances.isEmpty()) GlobalException.cast("媒资服务不可用，暂不能绑定");
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(3000);
        try {
            Map<?, ?> file = RestClient.builder()
                .baseUrl(instances.get(0).getUri().toString())
                .requestFactory(requestFactory)
                .build()
                .get()
                .uri("/media/files/{id}/binding-info", mediaId)
                .retrieve()
                .body(Map.class);
            if (
                file == null ||
                !mediaId.equals(file.get("id")) ||
                !companyId.toString().equals(String.valueOf(file.get("companyId")))
            ) {
                GlobalException.cast("媒资不存在或不属于当前机构");
            }
            if (!PROCESS_READY.equals(file.get("status"))) {
                GlobalException.cast("媒资尚未处理完成，暂不能绑定");
            }
            return file;
        } catch (HttpClientErrorException.NotFound error) {
            GlobalException.cast("媒资不存在或不属于当前机构");
            return Map.of();
        } catch (org.springframework.web.client.RestClientException error) {
            GlobalException.cast("无法核对媒资文件，请检查媒资服务");
            return Map.of();
        }
    }

    /**
     * 锁定所属课程，核对媒资文件存在、归属及处理状态后才替换旧绑定。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void associationMedia(BindTeachplanMediaDto bindTeachplanMediaDto) {
        if (
            bindTeachplanMediaDto == null ||
            bindTeachplanMediaDto.getTeachplanId() == null ||
            bindTeachplanMediaDto.getMediaId() == null ||
            bindTeachplanMediaDto.getMediaId().isBlank()
        ) {
            GlobalException.cast("教学计划或媒资编号不能为空");
        }
        Long teachplanId = bindTeachplanMediaDto.getTeachplanId();
        Teachplan teachplan = teachplanMapper.selectById(teachplanId);
        if (teachplan == null) {
            GlobalException.cast("教学计划不存在");
        }

        CourseBase course = courseBaseMapper.selectOne(
            new LambdaQueryWrapper<CourseBase>()
                .eq(CourseBase::getId, teachplan.getCourseId())
                .last("FOR UPDATE")
        );
        if (course == null || !companyId.equals(course.getCompanyId())) {
            GlobalException.cast("不能绑定非本机构课程的媒资");
        }
        if (
            !COURSE_DRAFT.equals(course.getStatus()) && !COURSE_OFFLINE.equals(course.getStatus())
        ) {
            GlobalException.cast("已发布或状态异常的课程不能修改媒资绑定");
        }
        if (AUDIT_PENDING.equals(course.getAuditStatus())) {
            GlobalException.cast("审核中的课程不能修改媒资绑定");
        }
        if (teachplan.getGrade() != 2) GlobalException.cast("只能给小节绑定媒资");
        // 使用媒资服务返回的文件名，不能信任客户端传来的名称。
        Map<?, ?> file = requireReadyMedia(bindTeachplanMediaDto.getMediaId());

        // 同一个教学计划采用替换绑定：先删除旧关联，再插入新关联，异常时事务回滚。
        teachplanMediaMapper.delete(
            new LambdaQueryWrapper<TeachplanMedia>().eq(TeachplanMedia::getTeachplanId, teachplanId)
        );

        TeachplanMedia teachplanMedia = new TeachplanMedia();
        teachplanMedia.setTeachplanId(teachplanId);
        // 关联记录的课程编号取自已查询的教学计划，避免由请求重复指定。
        teachplanMedia.setCourseId(teachplan.getCourseId());
        teachplanMedia.setMediaId(bindTeachplanMediaDto.getMediaId());
        teachplanMedia.setMediaFilename(String.valueOf(file.get("filename")));
        teachplanMedia.setCreateDate(LocalDateTime.now());

        int result = teachplanMediaMapper.insert(teachplanMedia);
        if (result > 0) {
            // 更换媒资后旧审核快照不再代表当前课程，须重新提交审核。
            if (AUDIT_APPROVED.equals(course.getAuditStatus())) {
                course.setAuditStatus(AUDIT_DRAFT);
                course.setChangeDate(LocalDateTime.now());
                if (courseBaseMapper.updateById(course) != 1) {
                    GlobalException.cast("课程审核状态更新失败");
                }
                coursePublishPreMapper.deleteById(course.getId());
            }
            log.info(
                "媒资绑定成功，teachplanId: {}, mediaId: {}",
                teachplanId,
                bindTeachplanMediaDto.getMediaId()
            );
        } else {
            log.error("媒资绑定失败，teachplanId: {}", teachplanId);
            throw new GlobalException("媒资绑定失败");
        }
        return;
    }
}
