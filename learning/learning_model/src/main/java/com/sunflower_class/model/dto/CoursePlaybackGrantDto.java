package com.sunflower_class.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 播放核对结果仅包含发布小节的媒资及机构，不包含对象路径或签名地址。 */
@Schema(description = "播放核对结果仅包含发布小节的媒资及机构，不包含对象路径或签名地址。")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CoursePlaybackGrantDto {

    /** 课程编号。 */
    @Schema(description = "课程编号。")
    private long courseId;

    /** 教学计划编号。 */
    @Schema(description = "教学计划编号。")
    private long lessonId;

    /** 媒资编号。 */
    @Schema(description = "媒资编号。")
    private String mediaId;

    /** 已发布课程所属教学空间编号。 */
    @Schema(description = "已发布课程所属教学空间编号。")
    private long companyId;
}
