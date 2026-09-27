package com.sunflower_class.base.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 协调 Windows、WSL 和 Docker 的视频转码工具，负责文件搬运、进程执行和临时文件清理。
 */
@Slf4j
@Component
public class FfmpegUtils {

    @org.springframework.beans.factory.annotation.Autowired
    private com.sunflower_class.base.config.FFmpegConfig config;

    /**
     * 将本地视频复制到 WSL，调用 Docker 中的 FFmpeg 转码并取回输出；成功返回 true，失败返回 false。
     */
    public boolean executeTranscode(String inputPath, String outputPath) {
        if (
            inputPath == null || inputPath.isEmpty() || outputPath == null || outputPath.isEmpty()
        ) {
            log.error("输入或输出路径为空");
            return false;
        }

        String inputFileName = getFileName(inputPath);
        String outputFileName = getFileName(outputPath);

        String uid = UUID.randomUUID().toString().substring(0, 8);
        String wslInputPath = config.getHostDataDir() + "/" + uid + "_" + inputFileName;
        String wslOutputPath = config.getHostDataDir() + "/" + uid + "_" + outputFileName;

        log.info("输入文件: {}, 输出文件: {}", inputPath, outputPath);

        try {
            ensureWslWorkDir();

            if (!copyToWsl(inputPath, wslInputPath)) {
                log.error("复制文件到 WSL 失败");
                return false;
            }

            List<String> command = new ArrayList<>();
            command.add("wsl");
            command.add("-d");
            command.add(config.getWslDistro());
            command.add("docker");
            command.add("run");
            command.add("--rm");
            command.add("-v");
            command.add(config.getHostDataDir() + ":" + config.getContainerDataDir());
            command.add(config.getImage());
            command.add("-i");
            command.add(config.getContainerDataDir() + "/" + uid + "_" + inputFileName);
            command.add("-vcodec");
            command.add(config.getVideoCodec());
            command.add("-crf");
            command.add(String.valueOf(config.getCrf()));
            command.add("-preset");
            command.add(config.getPreset());
            command.add("-acodec");
            command.add(config.getAudioCodec());
            command.add("-b:a");
            command.add(config.getAudioBitrate());
            command.add("-pix_fmt");
            command.add(config.getPixFormat());
            if (Boolean.TRUE.equals(config.getFastStart())) {
                command.add("-movflags");
                command.add("+faststart");
            }
            command.add("-y");
            command.add(config.getContainerDataDir() + "/" + uid + "_" + outputFileName);

            log.info("执行转码命令: {}", String.join(" ", command));

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            try (
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
                )
            ) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("FFmpeg: {}", line);
                }
            }

            boolean finished = process.waitFor(config.getTimeoutMinutes(), TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                log.error("FFmpeg 转码超时（超过 {} 分钟）", config.getTimeoutMinutes());
                return false;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("FFmpeg 转码失败，退出码: {}", exitCode);
                return false;
            }

            if (!copyFromWsl(wslOutputPath, outputPath)) {
                log.error("复制转码文件回 Windows 失败");
                return false;
            }

            log.info("FFmpeg 转码成功: {}", outputPath);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("转码线程被中断", e);
            return false;
        } catch (Exception e) {
            log.error("转码异常", e);
            return false;
        } finally {
            cleanWslFiles(wslInputPath, wslOutputPath);
        }
    }

    /**
     * 在配置的 WSL 发行版中创建转码工作目录，供 Docker 挂载使用。
     */
    private void ensureWslWorkDir() {
        try {
            String[] cmd = {
                "wsl",
                "-d",
                config.getWslDistro(),
                "mkdir",
                "-p",
                config.getHostDataDir(),
            };
            Process p = new ProcessBuilder(cmd).start();
            p.waitFor(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("创建 WSL 工作目录失败: {}", e.getMessage());
        }
    }

    /**
     * 将 Windows 盘符路径转换为 WSL 的 /mnt 路径，用于跨系统复制文件。
     */
    private String toWslMountPath(String windowsPath) {
        String normalized = windowsPath.replace("\\", "/");
        if (normalized.length() >= 2 && normalized.charAt(1) == ':') {
            char drive = Character.toLowerCase(normalized.charAt(0));
            return "/mnt/" + drive + normalized.substring(2);
        }
        return normalized;
    }

    /**
     * 将 Windows 输入文件复制到指定 WSL 路径，以布尔值表示复制是否成功。
     */
    private boolean copyToWsl(String windowsPath, String wslPath) {
        try {
            String mntPath = toWslMountPath(windowsPath);
            String[] cmd = { "wsl", "-d", config.getWslDistro(), "cp", mntPath, wslPath };

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
                )
            ) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.warn("copyToWsl: {}", line);
                }
            }

            boolean finished = process.waitFor(10, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                log.error("拷贝到 WSL 超时");
                return false;
            }

            if (process.exitValue() == 0) {
                log.info("复制到 WSL 成功: {}", wslPath);
                return true;
            } else {
                log.error("复制到 WSL 失败，退出码: {}", process.exitValue());
                return false;
            }
        } catch (Exception e) {
            log.error("复制到 WSL 异常", e);
            return false;
        }
    }

    /**
     * 将 WSL 转码结果复制回 Windows 输出路径，以布尔值表示复制是否成功。
     */
    private boolean copyFromWsl(String wslPath, String windowsPath) {
        try {
            File parentDir = new File(windowsPath).getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            String mntPath = toWslMountPath(windowsPath);
            String[] cmd = { "wsl", "-d", config.getWslDistro(), "cp", wslPath, mntPath };

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
                )
            ) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.warn("copyFromWsl: {}", line);
                }
            }

            boolean finished = process.waitFor(10, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                log.error("从 WSL 拷贝超时");
                return false;
            }

            if (process.exitValue() == 0) {
                log.info("从 WSL 复制成功: {}", windowsPath);
                return true;
            } else {
                log.error("从 WSL 复制失败，退出码: {}", process.exitValue());
                return false;
            }
        } catch (Exception e) {
            log.error("从 WSL 复制异常", e);
            return false;
        }
    }

    /**
     * 尝试清理本次任务在 WSL 中的输入和输出文件，释放临时存储空间。
     */
    private void cleanWslFiles(String inputPath, String outputPath) {
        try {
            String[] cmd = {
                "wsl",
                "-d",
                config.getWslDistro(),
                "rm",
                "-f",
                inputPath,
                outputPath,
            };
            Process p = new ProcessBuilder(cmd).start();
            p.waitFor(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("清理 WSL 临时文件失败: {}", e.getMessage());
        }
    }

    /**
     * 提取路径末尾的文件名，用于构建本次转码的临时文件名称。
     */
    private String getFileName(String path) {
        if (path == null || path.isEmpty()) {
            return "temp_file";
        }
        return new File(path).getName();
    }
}
