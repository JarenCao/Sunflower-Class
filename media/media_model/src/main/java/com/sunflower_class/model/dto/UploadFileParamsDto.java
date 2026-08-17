package com.sunflower_class.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "上传文件请求参数")
public class UploadFileParamsDto {

    @Schema(description = "文件名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "课程介绍视频.mp4")
    private String filename;

    @Schema(description = "文件类型（20101=图片，20102=视频，20103=其它）", example = "20101", allowableValues = {"20101", "20102", "20103"})
    private String fileType;

    @Schema(description = "文件大小（字节）", example = "10485760")
    private Long fileSize;

    @Schema(description = "文件标签", example = "Java,Spring,教程")
    private String tags;

    @Schema(description = "上传人", example = "admin")
    private String username;

    @Schema(description = "备注", example = "课程第1章视频")
    private String remark;
}