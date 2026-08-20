package com.sunflower_class.service.content.service.impl;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sunflower_class.base.exception.GlobalException;
import com.sunflower_class.model.dto.BindTeachplanMediaDto;
import com.sunflower_class.model.po.Teachplan;
import com.sunflower_class.model.po.TeachplanMedia;
import com.sunflower_class.service.content.mapper.TeachplanMapper;
import com.sunflower_class.service.content.mapper.TeachplanMediaMapper;
import com.sunflower_class.service.content.service.AssociationMediaService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AssociationMediaServiceImpl implements AssociationMediaService {

    @Autowired
    private TeachplanMapper teachplanMapper;

    @Autowired
    private TeachplanMediaMapper teachplanMediaMapper;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void associationMedia(BindTeachplanMediaDto bindTeachplanMediaDto) {
        Long teachplanId = bindTeachplanMediaDto.getTeachplanId();
        Teachplan teachplan = teachplanMapper.selectById(teachplanId);
        if (teachplan == null) {
            GlobalException.cast("教学计划不存在");
        }

        teachplanMediaMapper
                .delete(new LambdaQueryWrapper<TeachplanMedia>().eq(TeachplanMedia::getTeachplanId, teachplanId));

        TeachplanMedia teachplanMedia = new TeachplanMedia();
        teachplanMedia.setTeachplanId(teachplanId);
        teachplanMedia.setCourseId(teachplan.getCourseId());
        teachplanMedia.setMediaId(bindTeachplanMediaDto.getMediaId());
        teachplanMedia.setMediaFilename(bindTeachplanMediaDto.getFileName());
        teachplanMedia.setCreateDate(LocalDateTime.now());

        int result = teachplanMediaMapper.insert(teachplanMedia);
        if (result > 0) {
            log.info("媒资绑定成功，teachplanId: {}, mediaId: {}", teachplanId, bindTeachplanMediaDto.getMediaId());
        } else {
            log.error("媒资绑定失败，teachplanId: {}", teachplanId);
            throw new GlobalException("媒资绑定失败");
        }
        return;
    }

}
