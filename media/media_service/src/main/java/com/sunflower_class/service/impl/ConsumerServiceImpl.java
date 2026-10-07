package com.sunflower_class.service.impl;

import static com.sunflower_class.base.model.BusinessCodes.PROCESS_DELETING;
import static com.sunflower_class.base.model.BusinessCodes.PROCESS_FAILED;
import static com.sunflower_class.base.model.BusinessCodes.PROCESS_READY;
import static com.sunflower_class.base.model.BusinessCodes.PROCESS_RUNNING;
import static com.sunflower_class.base.model.BusinessCodes.PROCESS_WAITING;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rabbitmq.client.Channel;
import com.sunflower_class.base.utils.FfmpegUtils;
import com.sunflower_class.config.MinioConfig;
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
import io.minio.RemoveObjectArgs;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 视频转码消费者，负责任务抢占、文件转换、结果归档以及失败确认策略。
 */
@Slf4j
@Service
@RabbitListener(queues = "video.queue")
public class ConsumerServiceImpl implements ConsumerService {

    @Value("${media.transcode.max-attempts:3}")
    private int maxAttempts;

    @Autowired
    private MinioConfig minioConfig;

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

    /** 使用框架事务模板，确保类内调用也真正提交事务后再确认 RabbitMQ 消息。 */
    @Autowired
    private TransactionTemplate transactions;

    /** 重试时间由数据库统一计算，避免Windows与Docker数据库时区不同。 */

    @Value("${media.transcode.retry-delay-seconds:30}")
    private int retryDelay;

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
        MediaProcess mediaProcess = transactions.execute(status ->
            tryAcquireProcess(fileMd5, deliveryTag, channel)
        );
        if (mediaProcess == null) {
            return;
        }

        String tempInput = null;
        String tempOutput = null;

        try {
            String tempDir = System.getProperty("java.io.tmpdir");
            tempInput = Files.createTempFile(
                Path.of(tempDir),
                fileMd5 + "_",
                "_input.mp4"
            ).toString();
            tempOutput = Files.createTempFile(
                Path.of(tempDir),
                fileMd5 + "_",
                "_h264.mp4"
            ).toString();

            // 每次抢占使用独立临时文件，超时恢复的新旧消费者不能互相覆盖或删除文件。
            // 下载原始文件
            log.info("下载文件: bucket={}, object={}", msg.getBucket(), msg.getFilePath());
            downloadFromMinio(mediaProcess.getBucket(), mediaProcess.getFilePath(), tempInput);
            log.info("文件下载完成");

            // FFmpeg 转码
            log.info("开始转码...");
            boolean success = ffmpegUtils.executeTranscode(tempInput, tempOutput, () ->
                isCancelled(fileMd5)
            );
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
            transactions.executeWithoutResult(status ->
                markAsSuccess(fileMd5, outputBucket, outputPath, mediaProcess.getProcessingAt())
            );

            // 手动 ACK
            channel.basicAck(deliveryTag, false);
            log.info("转码全流程完成: fileMd5={}", fileMd5);
        } catch (Exception e) {
            log.error("转码失败: fileMd5={}", fileMd5, e);
            handleFailure(
                fileMd5,
                e.getMessage(),
                deliveryTag,
                channel,
                mediaProcess.getProcessingAt()
            );
            // 无论转码成功还是失败，都尝试清理本次下载和生成的本地临时文件。
        } finally {
            safeDelete(tempInput);
            safeDelete(tempOutput);
            if (isCancelled(fileMd5)) {
                try {
                    minioClient.removeObject(
                        RemoveObjectArgs.builder()
                            .bucket(minioConfig.getVideofiles())
                            .object("transcoded/" + fileMd5 + ".mp4")
                            .build()
                    );
                    // 取消清理结束后才清除运行标记，删除接口此前一直保留记录。
                    transactions.executeWithoutResult(transaction -> {
                        MediaProcess process = mediaProcessMapper.selectOne(
                            new LambdaQueryWrapper<MediaProcess>().eq(
                                MediaProcess::getFileId,
                                fileMd5
                            )
                        );
                        if (process != null) {
                            process.setStatus(PROCESS_DELETING);
                            process.setProcessingAt(null);
                            mediaProcessMapper.updateById(process);
                        }
                    });
                } catch (Exception error) {
                    log.warn("取消任务清理等待重试：{}", fileMd5, error);
                }
            }
        }
    }

    /**
     * 通过条件更新尝试抢占处理权；此方法本身没有声明事务
     * 返回 null 表示不需要处理（已在内部 ack）
     */
    private MediaProcess tryAcquireProcess(String fileMd5, long deliveryTag, Channel channel) {
        try {
            // 与删除及结果提交保持相同锁顺序：先媒资，再任务，避免并发取消死锁。
            MediaFiles file = mediaFilesMapper.selectOne(
                new LambdaQueryWrapper<MediaFiles>()
                    .eq(MediaFiles::getId, fileMd5)
                    .last("FOR UPDATE")
            );
            if (file == null || PROCESS_DELETING.equals(file.getStatus())) {
                channel.basicAck(deliveryTag, false);
                return null;
            }
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

            if (!PROCESS_WAITING.equals(status)) {
                log.warn("非法状态，跳过: fileMd5={}, status={}", fileMd5, status);
                channel.basicAck(deliveryTag, false);
                return null;
            }

            if (
                mediaProcess.getFailCount() != null &&
                mediaProcess.getFailCount() >= Math.max(1, maxAttempts)
            ) {
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
                Math.max(1, maxAttempts),
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
    public void markAsSuccess(
        String fileMd5,
        String outputBucket,
        String outputPath,
        LocalDateTime acquiredAt
    ) {
        MediaFiles mediaFiles = mediaFilesMapper.selectOne(
            new LambdaQueryWrapper<MediaFiles>().eq(MediaFiles::getId, fileMd5).last("FOR UPDATE")
        );
        if (
            mediaFiles == null || PROCESS_DELETING.equals(mediaFiles.getStatus())
        ) throw new IllegalStateException("转码任务已取消");
        MediaProcess active = mediaProcessMapper.selectOne(
            new LambdaQueryWrapper<MediaProcess>()
                .eq(MediaProcess::getFileId, fileMd5)
                .last("FOR UPDATE")
        );
        // 超时恢复后旧消费者不能提交结果，也不能归档新的任务。
        if (
            active == null ||
            !PROCESS_RUNNING.equals(active.getStatus()) ||
            !Objects.equals(acquiredAt, active.getProcessingAt())
        ) {
            throw new IllegalStateException("转码处理权已失效");
        }
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
    public void handleFailure(
        String fileMd5,
        String errorMsg,
        long deliveryTag,
        Channel channel,
        LocalDateTime acquiredAt
    ) {
        try {
            transactions.executeWithoutResult(transaction -> {
                MediaFiles lockedFile = mediaFilesMapper.selectOne(
                    new LambdaQueryWrapper<MediaFiles>()
                        .eq(MediaFiles::getId, fileMd5)
                        .last("FOR UPDATE")
                );
                if (lockedFile == null || PROCESS_DELETING.equals(lockedFile.getStatus())) return;
                MediaProcess process = mediaProcessMapper.selectOne(
                    new LambdaQueryWrapper<MediaProcess>()
                        .eq(MediaProcess::getFileId, fileMd5)
                        .last("FOR UPDATE")
                );
                if (
                    process == null ||
                    !PROCESS_RUNNING.equals(process.getStatus()) ||
                    !Objects.equals(acquiredAt, process.getProcessingAt())
                ) return;
                process.setStatus(PROCESS_FAILED);
                process.setFailCount(
                    (process.getFailCount() == null ? 0 : process.getFailCount()) + 1
                );
                process.setErrormsg(
                    errorMsg == null
                        ? "转码失败"
                        : errorMsg.substring(0, Math.min(1000, errorMsg.length()))
                );
                process.setRetryAt(null);
                process.setProcessingAt(null);
                mediaProcessMapper.updateById(process);
                mediaProcessMapper.scheduleProcessRetry(Math.max(1, retryDelay), process.getId());
                MediaFiles file = mediaFilesMapper.selectById(fileMd5);
                if (file != null) {
                    file.setStatus(PROCESS_FAILED);
                    mediaFilesMapper.updateById(file);
                }
            });
            // 失败已持久化，确认原消息；仅由定时任务按到期时间再次投递。
            channel.basicAck(deliveryTag, false);
        } catch (Exception error) {
            log.error("转码失败记录未提交，保留原消息：{}", fileMd5, error);
            try {
                channel.basicNack(deliveryTag, false, true);
            } catch (Exception nack) {
                log.error("消息退回失败", nack);
            }
        }
    }

    /** 删除标记是持久化取消信号，服务重启和迟到消息也不能把删除中的媒资改回可用。 */
    private boolean isCancelled(String id) {
        MediaFiles file = mediaFilesMapper.selectById(id);
        return file == null || PROCESS_DELETING.equals(file.getStatus());
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
