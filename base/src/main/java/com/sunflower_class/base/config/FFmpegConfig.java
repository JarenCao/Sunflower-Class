package com.sunflower_class.base.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "ffmpeg")
public class FFmpegConfig {

    /** Docker镜像名称 */
    private String image = "linuxserver/ffmpeg:6.1.1";

    /** 宿主机数据目录映射 */
    private String hostDataDir = System.getProperty("user.home") + "/ffmpeg";

    /** 容器内数据目录 */
    private String containerDataDir = "/data";

    /** 临时文件目录 */
    private String tempDir = "/data/temp";

    /** 视频编码器 */
    private String videoCodec = "libx264";

    /** 视频质量 (0-51, 越小质量越高) */
    private Integer crf = 18;

    /**
     * 编码速度预设: ultrafast, superfast, veryfast, faster, fast, medium, slow, slower, veryslow
     */
    private String preset = "medium";

    /** 音频编码器 */
    private String audioCodec = "libfdk_aac";

    /** 音频比特率 */
    private String audioBitrate = "192";

    /** 像素格式 */
    private String pixFormat = "yuv420p";

    /** 是否启用快速启动（流媒体优化） */
    private Boolean fastStart = true;
}
