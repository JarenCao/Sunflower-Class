package com.sunflower_class.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 学员公开目录响应；目录来自发布快照，不表示学习资格已经开通。 */
@Schema(description = "学员公开目录响应；目录来自发布快照，不表示学习资格已经开通。")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseDirectoryDto {

    /** 课程编号。 */
    @Schema(description = "课程编号。")
    private long id;

    /** 已发布教学计划的 JSON 快照。 */
    @Schema(description = "已发布教学计划的 JSON 快照。")
    private String teachplan;
}
