package com.sunflower_class.model.po;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@TableName("media_process")
@Schema(description = "媒资处理记录")
public class MediaProcess implements Serializable {

    @Schema(description = "主键ID", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "文件标识（OSS文件ID）", example = "file_123456")
    private String fileId;

    @Schema(description = "文件名称", example = "课程介绍视频.mp4")
    private String filename;

    @Schema(description = "存储桶名称", example = "course-media")
    private String bucket;

    @Schema(description = "文件存储路径", example = "/videos/2024/01/course.mp4")
    private String filePath;

    @Schema(description = "处理状态（1-未处理，2-处理完成）", example = "1", allowableValues = { "1", "2" })
    private String status;

    @Schema(description = "上传时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createDate;

    @Schema(description = "处理完成时间")
    private LocalDateTime finishDate;

    @Schema(description = "文件访问URL", example = "https://cdn.example.com/videos/course.mp4")
    private String url;

    @Schema(description = "处理失败原因", example = "视频格式不支持")
    private String errormsg;

    @Schema(description = "处理失败次数", example = "0")
    private Integer failCount;
}