package com.sunflower_class.base.utils;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.sunflower_class.base.config.FFmpegConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class FFmpegCommandBuilder {

    private final FFmpegConfig ffmpegConfig;

    /**
     * 构建Docker运行命令
     */
    public List<String> buildDockerCommand(List<String> ffmpegArgs) {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.add("run");
        command.add("--rm");

        // 挂载数据目录
        command.add("-v");
        command.add(ffmpegConfig.getHostDataDir() + ":" + ffmpegConfig.getContainerDataDir());

        // 使用镜像
        command.add(ffmpegConfig.getImage());

        // FFmpeg命令
        command.add("ffmpeg");
        command.addAll(ffmpegArgs);

        return command;
    }

    /**
     * 构建视频转码命令
     */
    public List<String> buildVideoTranscodeCommand(String inputFile, String outputFile) {
        List<String> args = new ArrayList<>();

        // 输入文件
        args.add("-i");
        args.add(inputFile);

        // 视频编码
        args.add("-c:v");
        args.add(ffmpegConfig.getVideoCodec());

        // 视频质量
        args.add("-crf");
        args.add(String.valueOf(ffmpegConfig.getCrf()));

        // 编码速度
        args.add("-preset");
        args.add(ffmpegConfig.getPreset());

        // 音频编码
        args.add("-c:a");
        args.add(ffmpegConfig.getAudioCodec());

        // 音频比特率
        args.add("-b:a");
        args.add(ffmpegConfig.getAudioBitrate());

        // 像素格式
        args.add("-pix_fmt");
        args.add(ffmpegConfig.getPixFormat());

        // 快速启动
        if (ffmpegConfig.getFastStart()) {
            args.add("-movflags");
            args.add("+faststart");
        }

        // 输出文件
        args.add(outputFile);

        return args;
    }

    /**
     * 构建音频转码命令
     */
    public List<String> buildAudioTranscodeCommand(String inputFile, String outputFile) {
        List<String> args = new ArrayList<>();

        args.add("-i");
        args.add(inputFile);

        // 只处理音频
        args.add("-vn");

        // 音频编码
        args.add("-c:a");
        args.add(ffmpegConfig.getAudioCodec());

        // 音频比特率
        args.add("-b:a");
        args.add(ffmpegConfig.getAudioBitrate());

        // 采样率
        args.add("-ar");
        args.add("44100");

        args.add(outputFile);

        return args;
    }

    /**
     * 构建获取视频信息命令
     */
    public List<String> buildProbeCommand(String inputFile) {
        List<String> args = new ArrayList<>();
        args.add("ffprobe");
        args.add("-v");
        args.add("quiet");
        args.add("-print_format");
        args.add("json");
        args.add("-show_format");
        args.add("-show_streams");
        args.add(inputFile);
        return args;
    }

    /**
     * 获取容器内路径
     */
    public String getContainerPath(String hostPath) {
        String dataDir = ffmpegConfig.getHostDataDir();
        if (hostPath.startsWith(dataDir)) {
            return hostPath.replace(dataDir, ffmpegConfig.getContainerDataDir());
        }
        return hostPath;
    }
}
