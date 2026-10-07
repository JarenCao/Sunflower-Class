package com.sunflower_class.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 短期播放地址仅对本次已验证的课程小节签发，不返回存储密钥。 */
@Schema(description = "短期播放地址仅对本次已验证的课程小节签发，不返回存储密钥。")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlaybackUrlDto {

    /** 课程编号。 */
    @Schema(description = "课程编号。")
    private long courseId;

    /** 教学计划编号。 */
    @Schema(description = "教学计划编号。")
    private long lessonId;

    /** 受保护的播放地址。 */
    @Schema(description = "受保护的播放地址。")
    private String url;

    /** 有效截止时间。 */
    @Schema(description = "有效截止时间。")
    private Instant expiresAt;
}
