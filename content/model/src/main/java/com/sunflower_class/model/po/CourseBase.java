package com.sunflower_class.model.po;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("course_base")
@Schema(description = "课程基本信息")
public class CourseBase implements Serializable {

    @Schema(description = "主键ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "机构ID", example = "10001")
    @TableField("company_id")
    private Long companyId;

    @Schema(description = "机构名称")
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

    @Schema(description = "大分类", example = "1-1")
    @TableField("mt")
    private String mt;

    @Schema(description = "小分类", example = "1-1-1")
    @TableField("st")
    private String st;

    @Schema(description = "课程等级（30301=初级，30302=中级，30303=高级）", example = "30301", allowableValues = { "30301", "30302",
            "30303" })
    @TableField("grade")
    private String grade;

    @Schema(description = "教学模式（30101=录播，30102=直播）", example = "30101", allowableValues = { "30101", "30102" })
    @TableField("teachmode")
    private String teachmode;

    @Schema(description = "课程介绍", example = "这是一门Java入门课程")
    @TableField("description")
    private String description;

    @Schema(description = "课程图片", example = "https://example.com/pic.jpg")
    @TableField("pic")
    private String pic;

    @Schema(description = "创建时间")
    @TableField(value = "create_date", fill = FieldFill.INSERT)
    private LocalDateTime createDate;

    @Schema(description = "修改时间")
    @TableField(value = "change_date", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime changeDate;

    @Schema(description = "审核状态", example = "30401:审核未通过,30402:未提交,30403:已提交,30404:审核通过")
    @TableField("audit_status")
    private String auditStatus;

    @Schema(description = "课程发布状态", example = "30501:未发布,30502:已发布,30503:下线")
    @TableField("status")
    private String status;
}