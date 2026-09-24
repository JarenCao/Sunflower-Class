package com.sunflower_class.model.po;

import java.io.Serializable;
import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 课程营销信息
 */
@Data
@TableName("course_market")
@Schema(description = "课程营销信息")
public class CourseMarket implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键，课程ID", example = "1")
    @TableId("id")
    private Long id;

    @Schema(description = "收费类型（70101=免费课程，70102=收费课程）", example = "70101", allowableValues = {"70101", "70102"})
    @TableField("charge")
    private String charge;

    @Schema(description = "课程现价", example = "199.00")
    @TableField("price")
    private BigDecimal price;

    @Schema(description = "课程原价", example = "299.00")
    @TableField("original_price")
    private BigDecimal originalPrice;

    @Schema(description = "咨询QQ", example = "123456789")
    @TableField("qq")
    private String qq;

    @Schema(description = "咨询微信", example = "wx_course")
    @TableField("wechat")
    private String wechat;

    @Schema(description = "咨询电话", example = "13800138000")
    @TableField("phone")
    private String phone;

    @Schema(description = "课程有效期（天）", example = "365")
    @TableField("valid_days")
    private Integer validDays;
}