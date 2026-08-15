package com.sunflower_class.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "新增课程计划信息")
public class AddTeachPlanDto {

    @Schema(description = "课程计划ID（更新时传入）", example = "1")
    private Long id;

    @Schema(description = "课程计划名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "第一章 Java基础入门")
    @NotBlank(message = "课程计划名称不能为空")
    private String pname;

    @Schema(description = "父级ID（0表示根节点）", example = "0")
    private Long parentId;

    @Schema(description = "层级（1-章节，2-小节，3-课时）", example = "1", allowableValues = {"1", "2", "3"})
    private Integer grade;

    @Schema(description = "课程类型（1-视频，2-文档）", example = "1", allowableValues = {"1", "2"})
    private String mediaType;

    @Schema(description = "课程ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "课程ID不能为空")
    private Long courseId;

    @Schema(description = "课程发布ID", example = "1")
    private Long coursePubId;

    @Schema(description = "是否支持试学预览（1-是，0-否）", example = "1", allowableValues = {"0", "1"})
    private String isPreview;
}