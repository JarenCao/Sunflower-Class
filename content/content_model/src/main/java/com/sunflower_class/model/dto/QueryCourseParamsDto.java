package com.sunflower_class.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "课程查询条件")
public class QueryCourseParamsDto {

    @Schema(description = "审核状态", example = "30401:审核未通过,30402:未提交,30403:已提交,30404:审核通过")
    private String auditStatus;

    @Schema(description = "课程名称", example = "Java")
    private String courseName;

    @Schema(description = "课程发布状态", example = "30501:未发布,30502:已发布,30503:下线")
    private String publishStatus;
}