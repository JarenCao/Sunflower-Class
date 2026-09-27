package com.sunflower_class.service.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sunflower_class.base.exception.GlobalException;
import com.sunflower_class.model.dto.BindTeachplanMediaDto;
import com.sunflower_class.model.po.Teachplan;
import com.sunflower_class.model.po.TeachplanMedia;
import com.sunflower_class.service.content.mapper.TeachplanMapper;
import com.sunflower_class.service.content.mapper.TeachplanMediaMapper;
import com.sunflower_class.service.content.service.AssociationMediaService;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 替换教学计划的媒资关联，使用事务保护旧关联删除与新关联插入。
 */
@Slf4j
@Service
public class AssociationMediaServiceImpl implements AssociationMediaService {

    @Autowired
    private TeachplanMapper teachplanMapper;

    @Autowired
    private TeachplanMediaMapper teachplanMediaMapper;

    /**
     * 检查教学计划存在后，在事务中删除旧媒资关联并写入新关联；当前未校验媒资可用性和登录机构。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void associationMedia(BindTeachplanMediaDto bindTeachplanMediaDto) {
        Long teachplanId = bindTeachplanMediaDto.getTeachplanId();
        Teachplan teachplan = teachplanMapper.selectById(teachplanId);
        if (teachplan == null) {
            GlobalException.cast("教学计划不存在");
        }

        // 同一个教学计划采用替换绑定：先删除旧关联，再插入新关联，异常时事务回滚。
        teachplanMediaMapper.delete(
            new LambdaQueryWrapper<TeachplanMedia>().eq(TeachplanMedia::getTeachplanId, teachplanId)
        );

        TeachplanMedia teachplanMedia = new TeachplanMedia();
        teachplanMedia.setTeachplanId(teachplanId);
        // 关联记录的课程编号取自已查询的教学计划，避免由请求重复指定。
        teachplanMedia.setCourseId(teachplan.getCourseId());
        teachplanMedia.setMediaId(bindTeachplanMediaDto.getMediaId());
        teachplanMedia.setMediaFilename(bindTeachplanMediaDto.getFileName());
        teachplanMedia.setCreateDate(LocalDateTime.now());

        int result = teachplanMediaMapper.insert(teachplanMedia);
        if (result > 0) {
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
