package com.sunflower_class.service.content.service;

import com.sunflower_class.model.dto.AddTeachPlanDto;
import com.sunflower_class.model.dto.TeachPlanDto;
import java.util.List;

/**
 * 教学计划查询与保存的业务契约。
 */
public interface TeachPlanService {
    /**
     * 查询课程计划树
     *
     * @param courseId 课程ID
     * @return 课程计划树形结构列表
     */
    public List<TeachPlanDto> findTeachPlanTree(Long courseId);

    /**
     * 校验章或小节层级，新建时追加同级排序号，更新时按编号修改并记录变更时间。
     */
    public void saveTeachPlan(AddTeachPlanDto addTeachPlanDto);
}
