package com.sunflower_class.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "编辑课程信息")
public class EditCourseDto extends AddCourseDto {
    
    @Schema(description = "课程id", example = "19")
    @NotBlank(message = "课程id不能为空")
    private Long id;
}
