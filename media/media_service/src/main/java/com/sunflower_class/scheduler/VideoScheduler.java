package com.sunflower_class.scheduler;

import com.sunflower_class.mapper.MediaProcessMapper;
import com.sunflower_class.model.dto.TranscodeMessageDto;
import com.sunflower_class.model.po.MediaProcess;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 扫描可重试的失败转码任务，更新任务状态并在事务提交后重新投递。
 */
@Slf4j
@Component
public class VideoScheduler {

    @Autowired
    private MediaProcessMapper mediaProcessMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @org.springframework.beans.factory.annotation.Value("${media.transcode.max-attempts:3}")
    private int maxAttempts;

    @org.springframework.beans.factory.annotation.Value("${media.transcode.retry-batch-size:10}")
    private int batchSize;

    /**
     * 定时查询未达重试上限的失败任务，改为待处理状态，并在事务提交后重新发送转码消息。
     */
    @Scheduled(cron = "${media.transcode.retry-cron:0 */5 * * * ?}")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void videoSchedulerTask() {
        List<MediaProcess> failedTasks = mediaProcessMapper.selectShedulerTasks(
            maxAttempts,
            batchSize
        );
        if (failedTasks == null || failedTasks.isEmpty()) {
            log.debug("没有未达到重试上限的失败任务");
            return;
        }

        log.info("查询到 {} 个未达到重试上限的失败任务", failedTasks.size());

        int count = 0;

        for (MediaProcess mediaProcess : failedTasks) {
            int updated = mediaProcessMapper.updateStatusFailTask(
                mediaProcess.getId(),
                maxAttempts,
                batchSize
            );

            if (updated > 0) {
                // 等待数据库事务提交后再发消息，防止消费者读到旧的任务状态。
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        /**
                         * 数据库事务提交成功后发送转码任务，避免消费者早于任务记录落库开始处理。
                         */
                        @Override
                        public void afterCommit() {
                            publishMessage(mediaProcess);
                        }
                    }
                );

                count++;

                log.info(
                    "任务已重新发送到MQ: fileId={}, 失败次数={}",
                    mediaProcess.getFileId(),
                    mediaProcess.getFailCount()
                );
            }
        }
        log.info("【定时任务】完成: 成功重新发送 {} 个任务", count);
    }

    /**
     * 从转码记录提取文件位置，通过直连交换机的 video 路由键重新发送任务。
     */
    public void publishMessage(MediaProcess mediaProcess) {
        TranscodeMessageDto msg = new TranscodeMessageDto();

        msg.setFileMd5(mediaProcess.getFileId());
        msg.setFilename(mediaProcess.getFilename());
        msg.setBucket(mediaProcess.getBucket());
        msg.setFilePath(mediaProcess.getFilePath());

        rabbitTemplate.convertAndSend("direct.exchange", "video", msg);
    }
}
