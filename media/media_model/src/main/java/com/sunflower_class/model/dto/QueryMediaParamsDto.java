package com.sunflower_class.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;

/**
 * 媒资文件查询请求模型类
 */
@Data
@ToString
@Schema(description = "媒资文件查询条件")
public class QueryMediaParamsDto {

    @Schema(description = "媒资文件名称", example = "课程视频")
    private String filename;

    @Schema(description = "媒资类型（20101=图片，20102=视频，20103=其它）", example = "20101", allowableValues = { "20101",
            "20102", "20103" })
    private String fileType;

    @Schema(description = "审核状态（20201=审核未通过，20202=未审核，20203=审核通过）", example = "20201", allowableValues = { "20201","20202", "20203" })
    private String auditStatus;
}