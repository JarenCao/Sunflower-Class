package com.sunflower_class.service.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;

import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rabbitmq.client.Channel;
import com.sunflower_class.base.utils.FfmpegUtils;
import com.sunflower_class.mapper.MediaFilesMapper;
import com.sunflower_class.mapper.MediaProcessHistoryMapper;
import com.sunflower_class.mapper.MediaProcessMapper;
import com.sunflower_class.model.dto.TranscodeMessageDto;
import com.sunflower_class.model.po.MediaFiles;
import com.sunflower_class.model.po.MediaProcess;
import com.sunflower_class.model.po.MediaProcessHistory;
import com.sunflower_class.service.ConsumerService;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RabbitListener(queues = "video.queue")
public class ConsumerServiceImpl implements ConsumerService {

    private static final int MAX_RETRY_COUNT = 3;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private MediaFilesMapper mediaFilesMapper;

    @Autowired
    private MediaProcessMapper mediaProcessMapper;

    @Autowired
    private MediaProcessHistoryMapper mediaProcessHistoryMapper;

    @Autowired
    private FfmpegUtils ffmpegUtils;

    @Override
    @RabbitHandler
    public void videoHandler(TranscodeMessageDto msg,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {

        String fileMd5 = msg.getFileMd5();
        log.info("收到转码消息: fileMd5={}, filename={}", fileMd5, msg.getFilename());

        MediaProcess mediaProcess = tryAcquireProcess(fileMd5, deliveryTag, channel);
        if (mediaProcess == null) {
            return;
        }

        String tempInput = null;
        String tempOutput = null;

        try {

            String tempDir = System.getProperty("java.io.tmpdir");
            tempInput = tempDir + File.separator + fileMd5 + "_input.mp4";
            tempOutput = tempDir + File.separator + fileMd5 + "_h264.mp4";

            // 下载原始文件
            log.info("下载文件: bucket={}, object={}", msg.getBucket(), msg.getFilePath());
            downloadFromMinio(msg.getBucket(), msg.getFilePath(), tempInput);
            log.info("文件下载完成");

            // FFmpeg 转码
            log.info("开始转码...");
            boolean success = ffmpegUtils.executeTranscode(tempInput, tempOutput);
            if (!success) {
                throw new RuntimeException("FFmpeg 转码失败");
            }
            log.info("转码完成");

            // 上传转码文件
            String outputBucket = "video";
            String outputPath = "transcoded/" + fileMd5 + ".mp4";
            log.info("上传转码文件: bucket={}, object={}", outputBucket, outputPath);
            uploadToMinio(outputBucket, outputPath, tempOutput);
            log.info("转码文件上传完成");

            // 更新结果
            markAsSuccess(fileMd5, outputBucket, outputPath);

            // 手动 ACK
            channel.basicAck(deliveryTag, false);
            log.info("转码全流程完成: fileMd5={}", fileMd5);

        } catch (Exception e) {
            log.error("转码失败: fileMd5={}", fileMd5, e);
            handleFailure(fileMd5, e.getMessage(), deliveryTag, channel);

        } finally {
            safeDelete(tempInput);
            safeDelete(tempOutput);
        }
    }

    /**
     * 尝试抢占处理权，短事务
     * 返回 null 表示不需要处理（已在内部 ack）
     */
    private MediaProcess tryAcquireProcess(String fileMd5, long deliveryTag, Channel channel) {
        try {

            LambdaQueryWrapper<MediaProcessHistory> historyWrapper = new LambdaQueryWrapper<>();
            historyWrapper.eq(MediaProcessHistory::getFileId, fileMd5);
            if (mediaProcessHistoryMapper.selectCount(historyWrapper) > 0) {
                log.info("文件已转码完成（历史记录），跳过: fileMd5={}", fileMd5);
                channel.basicAck(deliveryTag, false);
                return null;
            }

            LambdaQueryWrapper<MediaProcess> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(MediaProcess::getFileId, fileMd5);
            MediaProcess mediaProcess = mediaProcessMapper.selectOne(wrapper);

            if (mediaProcess == null) {
                log.error("视频处理记录不存在: fileMd5={}", fileMd5);

                channel.basicAck(deliveryTag, false);
                return null;
            }

            String status = mediaProcess.getStatus();

            if ("2".equals(status)) {
                log.info("文件已转码完成，跳过: fileMd5={}", fileMd5);
                channel.basicAck(deliveryTag, false);
                return null;
            }

            if ("4".equals(status)) {
                log.info("文件正在处理中，跳过: fileMd5={}", fileMd5);
                channel.basicAck(deliveryTag, false);
                return null;
            }

            if (!"1".equals(status) && !"3".equals(status)) {
                log.warn("非法状态，跳过: fileMd5={}, status={}", fileMd5, status);
                channel.basicAck(deliveryTag, false);
                return null;
            }

            if (mediaProcess.getFailCount() != null
                    && mediaProcess.getFailCount() >= MAX_RETRY_COUNT) {
                log.error("超过最大重试次数，跳过: fileMd5={}, failCount={}",
                        fileMd5, mediaProcess.getFailCount());
                channel.basicAck(deliveryTag, false);
                return null;
            }

            int updated = mediaProcessMapper.updateStatusIfProcessing(
                    String.valueOf(mediaProcess.getId()), 3, "4");

            if (updated == 0) {
                log.warn("抢占失败，可能已被其他节点处理: fileMd5={}", fileMd5);

                channel.basicNack(deliveryTag, false, true);
                return null;
            }

            log.info("抢占成功，状态更新为处理中: fileMd5={}", fileMd5);

            mediaProcess = mediaProcessMapper.selectById(mediaProcess.getId());

            return mediaProcess;

        } catch (Exception e) {
            log.error("抢占处理权异常: fileMd5={}", fileMd5, e);
            try {
                channel.basicNack(deliveryTag, false, true);
            } catch (Exception ex) {
                log.error("Nack 失败", ex);
            }
            return null;
        }
    }

    /**
     * 标记转码成功
     */
    @Transactional(rollbackFor = Exception.class)
    public void markAsSuccess(String fileMd5, String outputBucket, String outputPath) {

        MediaFiles mediaFiles = mediaFilesMapper.selectById(fileMd5);
        if (mediaFiles != null) {
            mediaFiles.setUrl(outputPath);
            mediaFilesMapper.updateById(mediaFiles);
        }

        LambdaQueryWrapper<MediaProcess> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MediaProcess::getFileId, fileMd5);
        MediaProcess mediaProcess = mediaProcessMapper.selectOne(wrapper);

        if (mediaProcess != null) {
            mediaProcess.setStatus("2");
            mediaProcess.setUrl("/" + outputBucket + "/" + outputPath);
            mediaProcess.setErrormsg(null);
            mediaProcess.setFinishDate(LocalDateTime.now());
            mediaProcessMapper.updateById(mediaProcess);

            MediaProcessHistory history = new MediaProcessHistory();
            BeanUtils.copyProperties(mediaProcess, history);
            mediaProcessHistoryMapper.insert(history);

            mediaProcessMapper.deleteById(mediaProcess.getId());
        }
    }

    /**
     * 处理转码失败
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleFailure(String fileMd5, String errorMsg,
            long deliveryTag, Channel channel) {
        try {
            LambdaQueryWrapper<MediaProcess> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(MediaProcess::getFileId, fileMd5);
            MediaProcess process = mediaProcessMapper.selectOne(wrapper);

            if (process != null) {

                int failCount = (process.getFailCount() == null ? 0 : process.getFailCount()) + 1;
                process.setStatus("3");
                process.setErrormsg(errorMsg);
                process.setFailCount(failCount);
                mediaProcessMapper.updateById(process);

                log.info("转码失败状态已记录: fileMd5={}, failCount={}", fileMd5, failCount);

                if (failCount >= MAX_RETRY_COUNT) {
                    log.error("达到最大重试次数，消息丢弃: fileMd5={}", fileMd5);
                    channel.basicAck(deliveryTag, false);
                } else {
                    // 重新入队，稍后重试
                    channel.basicNack(deliveryTag, false, true);
                }
            } else {
                log.error("找不到处理记录，消息丢弃: fileMd5={}", fileMd5);
                channel.basicAck(deliveryTag, false);
            }
        } catch (Exception ex) {
            log.error("更新失败状态异常，消息重新入队: fileMd5={}", fileMd5, ex);
            try {
                channel.basicNack(deliveryTag, false, true);
            } catch (Exception e) {
                log.error("Nack 失败", e);
            }
        }
    }

    private void downloadFromMinio(String bucket, String objectPath, String localPath) {
        try (InputStream is = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucket).object(objectPath).build());
                FileOutputStream fos = new FileOutputStream(localPath)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
        } catch (Exception e) {
            throw new RuntimeException("MinIO 下载失败: " + bucket + "/" + objectPath, e);
        }
    }

    private void uploadToMinio(String bucket, String objectPath, String localPath) {
        try (FileInputStream fis = new FileInputStream(localPath)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectPath)
                            .stream(fis, new File(localPath).length(), -1)
                            .contentType("video/mp4")
                            .build());
        } catch (Exception e) {
            throw new RuntimeException("MinIO 上传失败: " + bucket + "/" + objectPath, e);
        }
    }

    private void safeDelete(String path) {
        if (path != null) {
            try {
                new File(path).delete();
            } catch (Exception ignored) {
            }
        }
    }
}