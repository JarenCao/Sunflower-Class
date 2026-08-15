package com.sunflower_class.service.content.service;

import java.util.List;

import com.sunflower_class.model.dto.AddTeachPlanDto;
import com.sunflower_class.model.dto.TeachPlanDto;

public interface TeachPlanService {

    /**
     * 查询课程计划树
     *
     * @param courseId 课程ID
     * @return 课程计划树形结构列表
     */
    public List<TeachPlanDto> findTeachPlanTree(Long courseId);

    public void saveTeachPlan(AddTeachPlanDto addTeachPlanDto);
}