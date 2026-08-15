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

@Data
@TableName("media_files")
@Schema(description = "媒资文件信息")
public class MediaFiles implements Serializable {

    @Schema(description = "主键ID", example = "1234567890")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    @Schema(description = "机构ID", example = "1001")
    private Long companyId;

    @Schema(description = "机构名称", example = "Sunflower Class")
    private String companyName;

    @Schema(description = "文件名称", example = "课程介绍视频.mp4")
    private String filename;

    @Schema(description = "文件类型（001001=图片，001002=视频，001003=其它）", example = "001002", allowableValues = { "001001",
            "001002", "001003" })
    private String fileType;

    @Schema(description = "文件标签", example = "Java,Spring,教程")
    private String tags;

    @Schema(description = "存储桶名称", example = "course-media")
    private String bucket;

    @Schema(description = "文件存储路径", example = "/videos/2024/01/course.mp4")
    private String filePath;

    @Schema(description = "文件标识（OSS文件ID）", example = "file_123456")
    private String fileId;

    @Schema(description = "文件访问URL", example = "https://cdn.example.com/videos/course.mp4")
    private String url;

    @Schema(description = "上传人", example = "admin")
    private String username;

    @Schema(description = "上传时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createDate;

    @Schema(description = "修改时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime changeDate;

    @Schema(description = "处理状态（1-未处理，2-处理完成）", example = "1", allowableValues = { "1", "2" })
    private String status;

    @Schema(description = "备注", example = "已转码完成")
    private String remark;

    @Schema(description = "审核状态（002001=审核未通过，002002=未审核，002003=审核通过）", example = "002003", allowableValues = { "002001",
            "002002", "002003" })
    private String auditStatus;

    @Schema(description = "审核意见", example = "内容合规，审核通过")
    private String auditMind;

    @Schema(description = "文件大小（字节）", example = "10485760")
    private Long fileSize;
}