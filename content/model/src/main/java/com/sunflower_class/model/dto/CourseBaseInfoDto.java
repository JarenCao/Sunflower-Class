package com.sunflower_class.model.dto;

import java.math.BigDecimal;

import com.sunflower_class.model.po.CourseBase;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "课程信息详情 DTO（包含课程基础信息 + 营销信息）")
public class CourseBaseInfoDto extends CourseBase {

    @Schema(description = "收费类型（30201=免费课程，30202=收费课程）", example = "30201", allowableValues = { "30201", "30202" })
    private String charge;

    @Schema(description = "课程现价", example = "199.00")
    private BigDecimal price;

    @Schema(description = "课程原价", example = "299.00")
    private BigDecimal originalPrice;

    @Schema(description = "咨询QQ", example = "123456789")
    private String qq;

    @Schema(description = "咨询微信", example = "wx_course")
    private String wechat;

    @Schema(description = "咨询电话", example = "13800138000")
    private String phone;

    @Schema(description = "课程有效期（天）", example = "365")
    private Integer validDays;

    @Schema(description = "大分类名称", example = "前端开发")
    private String mtName;

    @Schema(description = "小分类名称", example = "JavaScript")
    private String stName;
}