package com.sunflower_class.base.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * FFmpeg 转码配置：描述 WSL 发行版、容器镜像、挂载目录和音视频编码参数。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ffmpeg")
public class FFmpegConfig {

    /** Docker镜像名称 */
    private String image = "linuxserver/ffmpeg:6.1.1";

    /** 宿主机数据目录映射 */
    private String hostDataDir = "/tmp/xiaokuihua-ffmpeg";

    private String wslDistro = "Ubuntu-24.04";

    private long timeoutMinutes = 30;

    /** 容器内数据目录 */
    private String containerDataDir = "/data";

    /** 临时文件目录 */
    private String tempDir = "/data/temp";

    /** 视频编码器 */
    private String videoCodec = "libx264";

    /** 视频质量 */
    private Integer crf = 18;

    /** 编码速度 */
    private String preset = "fast";

    /** 音频编码器 */
    private String audioCodec = "aac";

    /** 音频比特率 */
    private String audioBitrate = "192k";

    /** 像素格式 */
    private String pixFormat = "yuv420p";

    /** 是否启用快速启动*/
    private Boolean fastStart = true;
}
