package com.sunflower_class.model.dto;

import com.sunflower_class.model.po.Teachplan;
import com.sunflower_class.model.po.TeachplanMedia;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/**
 * 教学计划树节点，组合计划实体、子节点以及关联媒资。
 */
@Schema(description = "教学计划树节点，组合计划实体、子节点以及关联媒资。")
@Data
public class TeachPlanDto extends Teachplan {

    List<TeachPlanDto> teachPlanTreeNodes;
    TeachplanMedia teachplanMedia;
}
