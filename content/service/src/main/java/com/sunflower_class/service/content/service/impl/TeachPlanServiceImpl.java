package com.sunflower_class.service.content.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sunflower_class.base.exception.GlobalException;
import com.sunflower_class.model.dto.AddTeachPlanDto;
import com.sunflower_class.model.dto.TeachPlanDto;
import com.sunflower_class.model.po.Teachplan;
import com.sunflower_class.service.content.mapper.TeachplanMapper;
import com.sunflower_class.service.content.service.TeachPlanService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TeachPlanServiceImpl implements TeachPlanService {

    @Autowired
    TeachplanMapper teachplanMapper;

    private Integer getTeachPlanMax(Long courseId, Long parentId) {
        LambdaQueryWrapper<Teachplan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Teachplan::getCourseId, courseId)
                .eq(Teachplan::getParentid, parentId)
                .orderByDesc(Teachplan::getOrderby)
                .last("LIMIT 1");
        Teachplan max = teachplanMapper.selectOne(wrapper);
        return max == null ? 0 : max.getOrderby();
    }

    @Override
    public List<TeachPlanDto> findTeachPlanTree(Long id) {
        return teachplanMapper.queryTreeNodes(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveTeachPlan(AddTeachPlanDto addTeachPlanDto) {
        if (addTeachPlanDto == null) {
            throw new GlobalException("教学计划参数不能为空");
        }

        Teachplan teachPlan = new Teachplan();
        BeanUtils.copyProperties(addTeachPlanDto, teachPlan);

        Long id = addTeachPlanDto.getId();

        if (id == null) {
            Integer countMax = getTeachPlanMax(addTeachPlanDto.getCourseId(), addTeachPlanDto.getParentId());
            teachPlan.setCreateDate(LocalDateTime.now());
            teachPlan.setOrderby(countMax + 1);
            teachplanMapper.insert(teachPlan);
            log.info("教学计划添加成功，ID：{}", teachPlan.getId());
        } else {
            Teachplan existing = teachplanMapper.selectById(id);
            if (existing == null) {
                throw new GlobalException("要更新的教学计划不存在，ID：" + id);
            }
            teachPlan.setChangeDate(LocalDateTime.now());
            teachplanMapper.updateById(teachPlan);
            log.info("教学计划更新成功，ID：{}", id);
        }
    }
}