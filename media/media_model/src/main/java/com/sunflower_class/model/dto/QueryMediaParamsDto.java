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

    @Schema(description = "媒资类型（001001=图片，001002=视频，001003=其它）", example = "001002", allowableValues = { "001001",
            "001002", "001003" })
    private String fileType;

    @Schema(description = "审核状态（002001=审核未通过，002002=未审核，002003=审核通过）", example = "002003", allowableValues = { "002001",
            "002002", "002003" })
    private String auditStatus;
}