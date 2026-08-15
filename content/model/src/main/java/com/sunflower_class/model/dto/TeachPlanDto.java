package com.sunflower_class.model.dto;

import java.util.List;

import com.sunflower_class.model.po.Teachplan;
import com.sunflower_class.model.po.TeachplanMedia;

import lombok.Data;

@Data
public class TeachPlanDto extends Teachplan{

    List<TeachPlanDto> teachPlanTreeNodes;
    TeachplanMedia teachplanMedia;

}
