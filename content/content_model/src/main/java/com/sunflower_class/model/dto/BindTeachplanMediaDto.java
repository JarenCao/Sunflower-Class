package com.sunflower_class.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 媒资绑定请求，将教学计划编号与媒资编号、文件名关联。
 */
@Data
@Schema(description = "绑定课程计划与媒资文件请求参数")
public class BindTeachplanMediaDto {

    @Schema(description = "媒资文件ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String mediaId;

    @Schema(description = "媒资文件名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fileName;

    @Schema(description = "课程计划ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long teachplanId;
}
