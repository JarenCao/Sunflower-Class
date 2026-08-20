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

/**
 * 课程计划媒资关联
 */
@Getter
@Setter
@TableName("teachplan_media")
@Schema(description = "课程计划媒资关联")
public class TeachplanMedia implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "媒资文件ID", example = "media_001")
    @TableField("media_id")
    private String mediaId;

    @Schema(description = "课程计划ID", example = "1")
    @TableField("teachplan_id")
    private Long teachplanId;

    @Schema(description = "课程ID", example = "1")
    @TableField("course_id")
    private Long courseId;

    @Schema(description = "媒资文件原始名称", example = "第一章视频.mp4")
    @TableField("media_fileName")
    private String mediaFilename;

    @Schema(description = "创建时间")
    @TableField(value = "create_date", fill = FieldFill.INSERT)
    private LocalDateTime createDate;

    @Schema(description = "创建人", example = "admin")
    @TableField("create_people")
    private String createPeople;

    @Schema(description = "修改人", example = "admin")
    @TableField("change_people")
    private String changePeople;
}