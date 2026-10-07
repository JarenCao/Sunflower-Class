package com.sunflower_class.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 课程讲师介绍表单；不接收机构、课程归属或登录权限。 */
@Data
@Schema(description = "课程师资维护表单")
public class CourseTeacherDto {

    @NotBlank(message = "讲师姓名不能为空")
    @Size(max = 60, message = "讲师姓名最多60个字符")
    @Schema(description = "讲师姓名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String teacherName;

    @Size(max = 255, message = "讲师职位最多255个字符")
    @Schema(description = "讲师职位")
    private String position;

    @NotBlank(message = "讲师介绍不能为空")
    @Size(max = 1024, message = "讲师介绍最多1024个字符")
    @Schema(description = "讲师介绍", requiredMode = Schema.RequiredMode.REQUIRED)
    private String introduction;

    @Size(max = 1024, message = "讲师照片地址最多1024个字符")
    @Schema(description = "讲师照片地址，可留空")
    private String photograph;
}
