package com.sunflower_class.model.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "视频转码消息")
public class TranscodeMessageDto implements Serializable {

    @Schema(description = "文件MD5值", requiredMode = Schema.RequiredMode.REQUIRED, example = "e10adc3949ba59abbe56e057f20f883e")
    private String fileMd5;

    @Schema(description = "文件名", requiredMode = Schema.RequiredMode.REQUIRED, example = "课程视频.mp4")
    private String filename;

    @Schema(description = "存储桶名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "course-media")
    private String bucket;

    @Schema(description = "文件存储路径", requiredMode = Schema.RequiredMode.REQUIRED, example = "/videos/2026/01/course.mp4")
    private String filePath;
}