package com.sunflower_class.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 课程修改请求，继承新增表单字段并补充待修改课程编号。
 */
@Data
@Schema(description = "编辑课程信息")
public class EditCourseDto extends AddCourseDto {

    @Schema(description = "课程id", example = "19")
    @NotNull(message = "课程id不能为空")
    private Long id;
}
