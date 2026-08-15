package com.sunflower_class.model.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 课程发布预发布
 * </p>
 *
 * @author jarencao
 * @since 2026-08-07
 */
@Getter
@Setter
@TableName("course_publish_pre")
@Schema(description = "课程预发布信息")
public class CoursePublishPre implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键", example = "1")
    @TableId("id")
    private Long id;

    @Schema(description = "机构ID", example = "1001")
    @TableField("company_id")
    private Long companyId;

    @Schema(description = "机构名称", example = "Sunflower Class")
    @TableField("company_name")
    private String companyName;

    @Schema(description = "课程名称", example = "Java从入门到精通")
    @TableField("name")
    private String name;

    @Schema(description = "适用人群", example = "Java初学者")
    @TableField("users")
    private String users;

    @Schema(description = "课程标签", example = "Java,Spring,微服务")
    @TableField("tags")
    private String tags;

    @Schema(description = "创建人", example = "admin")
    @TableField("username")
    private String username;

    @Schema(description = "大分类ID", example = "1-1")
    @TableField("mt")
    private String mt;

    @Schema(description = "大分类名称", example = "IT")
    @TableField("mt_name")
    private String mtName;

    @Schema(description = "小分类ID", example = "1-1-1")
    @TableField("st")
    private String st;

    @Schema(description = "小分类名称", example = "Java")
    @TableField("st_name")
    private String stName;

    @Schema(description = "课程等级", example = "30302")
    @TableField("grade")
    private String grade;

    @Schema(description = "教学模式", example = "30101")
    @TableField("teachmode")
    private String teachmode;

    @Schema(description = "课程封面图URL", example = "https://example.com/course.jpg")
    @TableField("pic")
    private String pic;

    @Schema(description = "课程介绍", example = "这是一门系统性的Java课程")
    @TableField("description")
    private String description;

    @Schema(description = "课程营销信息（JSON格式）")
    @TableField("market")
    private String market;

    @Schema(description = "课程计划（JSON格式）")
    @TableField("teachplan")
    private String teachplan;

    @Schema(description = "教师信息（JSON格式）")
    @TableField("teachers")
    private String teachers;

    @Schema(description = "提交时间")
    @TableField(value = "create_date", fill = FieldFill.INSERT)
    private LocalDateTime createDate;

    @Schema(description = "审核时间")
    @TableField("audit_date")
    private LocalDateTime auditDate;

    @Schema(description = "审核状态")
    @TableField("status")
    private String status;

    @Schema(description = "备注")
    @TableField("remark")
    private String remark;

    @Schema(description = "收费类型", example = "70102")
    @TableField("charge")
    private String charge;

    @Schema(description = "课程现价", example = "199.00")
    @TableField("price")
    private BigDecimal price;

    @Schema(description = "课程原价", example = "299.00")
    @TableField("original_price")
    private BigDecimal originalPrice;

    @Schema(description = "课程有效期（天）", example = "365")
    @TableField("valid_days")
    private Integer validDays;
}