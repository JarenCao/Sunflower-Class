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

@Slf4j
@Component
public class FfmpegUtils {

    private static final String WSL_WORK_DIR = "/home/jarencao/ffmpeg/temp";
    private static final String WSL_DISTRO = "Ubuntu-24.04";
    private static final long TRANSCODE_TIMEOUT_MINUTES = 30;

    public boolean executeTranscode(String inputPath, String outputPath) {
        if (inputPath == null || inputPath.isEmpty()
                || outputPath == null || outputPath.isEmpty()) {
            log.error("输入或输出路径为空");
            return false;
        }

        String inputFileName = getFileName(inputPath);
        String outputFileName = getFileName(outputPath);


        String uid = UUID.randomUUID().toString().substring(0, 8);
        String wslInputPath = WSL_WORK_DIR + "/" + uid + "_" + inputFileName;
        String wslOutputPath = WSL_WORK_DIR + "/" + uid + "_" + outputFileName;

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
            command.add(WSL_DISTRO);
            command.add("docker");
            command.add("run");
            command.add("--rm");
            command.add("-v");
            command.add(WSL_WORK_DIR + ":/data");
            command.add("linuxserver/ffmpeg:6.1.1");
            command.add("-i");
            command.add("/data/" + uid + "_" + inputFileName);
            command.add("-c:v");
            command.add("libx264");
            command.add("-crf");
            command.add("18");
            command.add("-preset");
            command.add("fast");
            command.add("-c:a");
            command.add("aac");
            command.add("-b:a");
            command.add("192k");
            command.add("-pix_fmt");
            command.add("yuv420p");
            command.add("-movflags");
            command.add("+faststart");
            command.add("-y");
            command.add("/data/" + uid + "_" + outputFileName);

            log.info("执行转码命令: {}", String.join(" ", command));

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("FFmpeg: {}", line);
                }
            }

            boolean finished = process.waitFor(TRANSCODE_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                log.error("FFmpeg 转码超时（超过 {} 分钟）", TRANSCODE_TIMEOUT_MINUTES);
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

    private void ensureWslWorkDir() {
        try {
            String[] cmd = {"wsl", "-d", WSL_DISTRO, "mkdir", "-p", WSL_WORK_DIR};
            Process p = new ProcessBuilder(cmd).start();
            p.waitFor(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("创建 WSL 工作目录失败: {}", e.getMessage());
        }
    }

    private String toWslMountPath(String windowsPath) {
        String normalized = windowsPath.replace("\\", "/");
        if (normalized.length() >= 2 && normalized.charAt(1) == ':') {
            char drive = Character.toLowerCase(normalized.charAt(0));
            return "/mnt/" + drive + normalized.substring(2);
        }
        return normalized;
    }

    private boolean copyToWsl(String windowsPath, String wslPath) {
        try {
            String mntPath = toWslMountPath(windowsPath);
            String[] cmd = {"wsl", "-d", WSL_DISTRO, "cp", mntPath, wslPath};

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
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

    private boolean copyFromWsl(String wslPath, String windowsPath) {
        try {
            File parentDir = new File(windowsPath).getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            String mntPath = toWslMountPath(windowsPath);
            String[] cmd = {"wsl", "-d", WSL_DISTRO, "cp", wslPath, mntPath};

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
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

    private void cleanWslFiles(String inputPath, String outputPath) {
        try {
            String[] cmd = {"wsl", "-d", WSL_DISTRO, "rm", "-f", inputPath, outputPath};
            Process p = new ProcessBuilder(cmd).start();
            p.waitFor(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("清理 WSL 临时文件失败: {}", e.getMessage());
        }
    }

    private String getFileName(String path) {
        if (path == null || path.isEmpty()) {
            return "temp_file";
        }
        return new File(path).getName();
    }
}