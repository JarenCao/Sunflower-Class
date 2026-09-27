package com.sunflower_class.service.impl;

import static com.sunflower_class.base.model.BusinessCodes.*;

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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 视频转码消费者，负责任务抢占、文件转换、结果归档以及失败确认策略。
 */
@Slf4j
@Service
@RabbitListener(queues = "video.queue")
public class ConsumerServiceImpl implements ConsumerService {

    @org.springframework.beans.factory.annotation.Value("${media.transcode.max-attempts:3}")
    private int maxAttempts;

    @Autowired
    private com.sunflower_class.config.MinioConfig minioConfig;

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

    /**
     * 消费转码消息并抢占任务，下载、转码和上传视频，再按处理结果确认消息或记录失败并重试。
     */
    @Override
    @RabbitHandler
    public void videoHandler(
        TranscodeMessageDto msg,
        Channel channel,
        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
    ) {
        String fileMd5 = msg.getFileMd5();
        log.info("收到转码消息: fileMd5={}, filename={}", fileMd5, msg.getFilename());

        // 收到消息后先判断历史结果与当前状态，再通过数据库条件更新争取处理权。
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
            String outputBucket = minioConfig.getVideofiles();
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
            // 无论转码成功还是失败，都尝试清理本次下载和生成的本地临时文件。
        } finally {
            safeDelete(tempInput);
            safeDelete(tempOutput);
        }
    }

    /**
     * 通过条件更新尝试抢占处理权；此方法本身没有声明事务
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

            if (PROCESS_READY.equals(status)) {
                log.info("文件已转码完成，跳过: fileMd5={}", fileMd5);
                channel.basicAck(deliveryTag, false);
                return null;
            }

            if (PROCESS_RUNNING.equals(status)) {
                log.info("文件正在处理中，跳过: fileMd5={}", fileMd5);
                channel.basicAck(deliveryTag, false);
                return null;
            }

            if (!PROCESS_WAITING.equals(status) && !PROCESS_FAILED.equals(status)) {
                log.warn("非法状态，跳过: fileMd5={}, status={}", fileMd5, status);
                channel.basicAck(deliveryTag, false);
                return null;
            }

            if (mediaProcess.getFailCount() != null && mediaProcess.getFailCount() >= maxAttempts) {
                log.error(
                    "超过最大重试次数，跳过: fileMd5={}, failCount={}",
                    fileMd5,
                    mediaProcess.getFailCount()
                );
                channel.basicAck(deliveryTag, false);
                return null;
            }

            // 以更新行数判断抢占结果，避免多个消费者同时处理同一条待转码记录。
            int updated = mediaProcessMapper.updateStatusIfProcessing(
                String.valueOf(mediaProcess.getId()),
                maxAttempts,
                PROCESS_RUNNING
            );

            if (updated == 0) {
                log.warn("抢占失败，可能已被其他节点处理: fileMd5={}", fileMd5);

                channel.basicNack(deliveryTag, false, true);
                return null;
            }

            MediaFiles runningFile = mediaFilesMapper.selectById(fileMd5);
            if (runningFile != null) {
                runningFile.setStatus(PROCESS_RUNNING);
                mediaFilesMapper.updateById(runningFile);
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
            mediaFiles.setUrl("/" + outputBucket + "/" + outputPath);
            mediaFiles.setStatus(PROCESS_READY);
            mediaFiles.setBucket(outputBucket);
            mediaFiles.setFilePath(outputPath);
            mediaFilesMapper.updateById(mediaFiles);
        }

        LambdaQueryWrapper<MediaProcess> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MediaProcess::getFileId, fileMd5);
        MediaProcess mediaProcess = mediaProcessMapper.selectOne(wrapper);

        if (mediaProcess != null) {
            mediaProcess.setStatus(PROCESS_READY);
            mediaProcess.setUrl("/" + outputBucket + "/" + outputPath);
            mediaProcess.setErrormsg(null);
            mediaProcess.setFinishDate(LocalDateTime.now());
            mediaProcessMapper.updateById(mediaProcess);

            // 成功任务复制到历史表后移出待处理表，后续重复消息可据此跳过。
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
    public void handleFailure(String fileMd5, String errorMsg, long deliveryTag, Channel channel) {
        try {
            LambdaQueryWrapper<MediaProcess> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(MediaProcess::getFileId, fileMd5);
            MediaProcess process = mediaProcessMapper.selectOne(wrapper);

            if (process != null) {
                int failCount = (process.getFailCount() == null ? 0 : process.getFailCount()) + 1;
                process.setStatus(PROCESS_FAILED);
                MediaFiles failedFile = mediaFilesMapper.selectById(fileMd5);
                if (failedFile != null) {
                    failedFile.setStatus(PROCESS_FAILED);
                    mediaFilesMapper.updateById(failedFile);
                }
                process.setErrormsg(errorMsg);
                process.setFailCount(failCount);
                mediaProcessMapper.updateById(process);

                log.info("转码失败状态已记录: fileMd5={}, failCount={}", fileMd5, failCount);

                // 达到配置上限后确认消息并保留失败记录，未达到上限则重新入队。
                if (failCount >= maxAttempts) {
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

    // 分块复制对象流到临时文件，避免将整个视频一次加载进内存。
    private void downloadFromMinio(String bucket, String objectPath, String localPath) {
        try (
            InputStream is = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucket).object(objectPath).build()
            );
            FileOutputStream fos = new FileOutputStream(localPath)
        ) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
        } catch (Exception e) {
            throw new RuntimeException("MinIO 下载失败: " + bucket + "/" + objectPath, e);
        }
    }

    /**
     * 将本地转码结果上传到指定桶和对象路径，按 video/mp4 标记内容类型。
     */
    private void uploadToMinio(String bucket, String objectPath, String localPath) {
        try (FileInputStream fis = new FileInputStream(localPath)) {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectPath)
                    .stream(fis, new File(localPath).length(), -1)
                    .contentType("video/mp4")
                    .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("MinIO 上传失败: " + bucket + "/" + objectPath, e);
        }
    }

    /**
     * 尝试删除非空路径对应的临时文件，忽略清理异常；不保证文件一定删除成功。
     */
    private void safeDelete(String path) {
        if (path != null) {
            try {
                new File(path).delete();
            } catch (Exception ignored) {}
        }
    }
}
