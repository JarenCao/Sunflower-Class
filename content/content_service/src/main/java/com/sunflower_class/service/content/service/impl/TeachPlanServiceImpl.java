package com.sunflower_class.service.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sunflower_class.base.exception.GlobalException;
import com.sunflower_class.model.dto.AddTeachPlanDto;
import com.sunflower_class.model.dto.TeachPlanDto;
import com.sunflower_class.model.po.Teachplan;
import com.sunflower_class.service.content.mapper.TeachplanMapper;
import com.sunflower_class.service.content.service.TeachPlanService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 维护课程章与小节，处理树形查询、父节点映射及新增节点排序。
 */
@Slf4j
@Service
public class TeachPlanServiceImpl implements TeachPlanService {

    @Autowired
    TeachplanMapper teachplanMapper;

    // 仅查询同一课程、同一父节点下的最大排序号，新节点追加到同级末尾。
    private Integer getTeachPlanMax(Long courseId, Long parentId) {
        LambdaQueryWrapper<Teachplan> wrapper = new LambdaQueryWrapper<>();
        wrapper
            .eq(Teachplan::getCourseId, courseId)
            .eq(Teachplan::getParentid, parentId)
            .orderByDesc(Teachplan::getOrderby)
            .last("LIMIT 1");
        Teachplan max = teachplanMapper.selectOne(wrapper);
        return max == null ? 0 : max.getOrderby();
    }

    /**
     * 通过映射查询返回指定课程的教学计划树，用于编排和发布快照。
     */
    @Override
    public List<TeachPlanDto> findTeachPlanTree(Long id) {
        return teachplanMapper.queryTreeNodes(id);
    }

    /**
     * 校验章或小节层级，新建时追加同级排序号，更新时按编号修改并记录变更时间。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveTeachPlan(AddTeachPlanDto addTeachPlanDto) {
        if (addTeachPlanDto == null) {
            throw new GlobalException("教学计划参数不能为空");
        }

        Teachplan teachPlan = new Teachplan();
        BeanUtils.copyProperties(addTeachPlanDto, teachPlan);
        // 请求字段 parentId 与实体 parentid 命名不同，需要显式赋值。
        teachPlan.setParentid(addTeachPlanDto.getParentId());
        if (
            addTeachPlanDto.getGrade() == null ||
            addTeachPlanDto.getGrade() < 1 ||
            addTeachPlanDto.getGrade() > 2
        ) {
            GlobalException.cast("请选择章节或小节层级");
        }
        // 校验层级只能是章或小节后，再转换为数据库实体使用的 short 类型。
        teachPlan.setGrade(addTeachPlanDto.getGrade().shortValue());
        teachPlan.setStatus(com.sunflower_class.base.model.BusinessCodes.RECORD_ACTIVE);

        Long id = addTeachPlanDto.getId();

        // 是否携带计划编号决定新增或更新；新增分配排序号，更新记录修改时间。
        if (id == null) {
            Integer countMax = getTeachPlanMax(
                addTeachPlanDto.getCourseId(),
                addTeachPlanDto.getParentId()
            );
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
