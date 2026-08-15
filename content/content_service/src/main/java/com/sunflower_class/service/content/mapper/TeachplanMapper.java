package com.sunflower_class.service.content.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunflower_class.model.dto.TeachPlanDto;
import com.sunflower_class.model.po.Teachplan;

public interface TeachplanMapper extends BaseMapper<Teachplan> {
    public List<TeachPlanDto> queryTreeNodes(Long id);
}
