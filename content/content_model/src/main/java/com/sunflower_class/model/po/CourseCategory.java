package com.sunflower_class.model.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * <p>
 * 课程分类
 * </p>
 *
 * @author jarencao
 * @since 2026-08-07
 */
@Getter
@Setter
@TableName("course_category")
@Schema(description = "课程分类")
public class CourseCategory implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID", example = "1")
    @TableId("id")
    private String id;

    @Schema(description = "分类名称", example = "Java")
    @TableField("name")
    private String name;

    @Schema(description = "分类标签（默认和名称一样）", example = "Java")
    @TableField("label")
    private String label;

    @Schema(description = "父结点ID（根节点的父节点是0）", example = "0")
    @TableField("parentid")
    private String parentid;

    @Schema(description = "是否显示 0-不显示 1-显示", example = "1")
    @TableField("is_show")
    private Byte isShow;

    @Schema(description = "排序字段（数字越小越靠前）", example = "1")
    @TableField("orderby")
    private Integer orderby;

    @Schema(description = "是否叶子节点 0-否 1-是", example = "1")
    @TableField("is_leaf")
    private Byte isLeaf;
}