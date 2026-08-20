package com.sunflower_class.model.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "新增课程信息")
public class AddCourseDto {

    @Schema(description = "课程名称", example = "Java从入门到精通")
    @NotBlank(message = "课程名称不能为空")
    private String name;

    @Schema(description = "适用人群", example = "Java初学者，有一定编程基础")
    private String users;

    @Schema(description = "课程标签", example = "Java,Spring,微服务")
    private String tags;

    @Schema(description = "大分类ID", example = "1-1")
    private String mt;

    @Schema(description = "小分类ID", example = "1-1-1")
    private String st;

    @Schema(description = "课程等级（30301=初级，30302=中级，30303=高级）", example = "30301", allowableValues = { "30301", "30302",
            "30303" })
    private String grade;

    @Schema(description = "教学模式（30101=录播，30102=直播）", example = "30101", allowableValues = { "30101", "30102" })
    private String teachmode;

    @Schema(description = "课程简介", example = "这是一门系统性的Java进阶课程")
    private String description;

    @Schema(description = "课程封面图URL", example = "https://example.com/images/course.jpg")
    private String pic;

    @Schema(description = "收费类型（30201=免费课程，30202=收费课程）", example = "30201", allowableValues = { "30201", "30202" })
    private String charge;

    @Schema(description = "课程价格", example = "199.00")
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
}