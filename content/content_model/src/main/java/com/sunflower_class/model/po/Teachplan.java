package com.sunflower_class.model.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 课程计划
 * </p>
 *
 * @author jarencao
 * @since 2026-08-07
 */
@Getter
@Setter
@TableName("teachplan")
@Schema(description = "课程计划")
public class Teachplan implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "课程计划名称", example = "第一章 Java基础")
    @TableField("pname")
    private String pname;

    @Schema(description = "父级ID", example = "0")
    @TableField("parentid")
    private Long parentid;

    @Schema(description = "层级（1、2、3级）", example = "1")
    @TableField("grade")
    private Short grade;

    @Schema(description = "课程类型（1-视频、2-文档）", example = "1")
    @TableField("media_type")
    private String mediaType;

    @Schema(description = "开始直播时间")
    @TableField("start_time")
    private LocalDateTime startTime;

    @Schema(description = "直播结束时间")
    @TableField("end_time")
    private LocalDateTime endTime;

    @Schema(description = "章节介绍")
    @TableField("description")
    private String description;

    @Schema(description = "时长（时:分:秒）", example = "01:30:00")
    @TableField("timelength")
    private String timelength;

    @Schema(description = "排序字段", example = "1")
    @TableField("orderby")
    private Integer orderby;

    @Schema(description = "课程ID", example = "1")
    @TableField("course_id")
    private Long courseId;

    @Schema(description = "课程发布ID", example = "1")
    @TableField("course_pub_id")
    private Long coursePubId;

    @Schema(description = "状态（1-正常，0-删除）", example = "1")
    @TableField("status")
    private Integer status;

    @Schema(description = "是否支持试学预览", example = "1")
    @TableField("is_preview")
    private String isPreview;

    @Schema(description = "创建时间")
    @TableField(value = "create_date", fill = FieldFill.INSERT)
    private LocalDateTime createDate;

    @Schema(description = "修改时间")
    @TableField(value = "change_date", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime changeDate;
}