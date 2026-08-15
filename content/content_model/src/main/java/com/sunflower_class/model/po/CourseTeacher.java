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
 * 课程-教师关系表
 * </p>
 *
 * @author jarencao
 * @since 2026-08-07
 */
@Getter
@Setter
@TableName("course_teacher")
@Schema(description = "课程教师信息")
public class CourseTeacher implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "课程ID", example = "1")
    @TableField("course_id")
    private Long courseId;

    @Schema(description = "教师姓名", example = "张三")
    @TableField("teacher_name")
    private String teacherName;

    @Schema(description = "教师职位", example = "高级讲师")
    @TableField("position")
    private String position;

    @Schema(description = "教师简介", example = "10年Java开发经验")
    @TableField("introduction")
    private String introduction;

    @Schema(description = "教师照片URL", example = "https://example.com/teacher.jpg")
    @TableField("photograph")
    private String photograph;

    @Schema(description = "创建时间")
    @TableField(value = "create_date", fill = FieldFill.INSERT)
    private LocalDateTime createDate;
}