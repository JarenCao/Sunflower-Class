package com.sunflower_class.scheduler;

import com.sunflower_class.base.config.FFmpegConfig;
import com.sunflower_class.base.course.CourseMessageSender;
import com.sunflower_class.mapper.MediaProcessMapper;
import com.sunflower_class.model.dto.TranscodeMessageDto;
import com.sunflower_class.model.po.MediaProcess;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** 定时任务统一处理待发送、到期失败与超时任务；消息重复由原子抢占排除。 */
@Slf4j
@Component
public class VideoScheduler {

    @Autowired
    private MediaProcessMapper tasks;

    @Autowired
    private CourseMessageSender sender;

    @Autowired
    private TransactionTemplate transactions;

    @Value("${media.transcode.max-attempts:3}")
    private int maxAttempts;

    @Value("${media.transcode.retry-batch-size:10}")
    private int batchSize;

    @Autowired
    private FFmpegConfig ffmpegConfig;

    /** 每五秒检查到期任务，失败次数由实际转码失败累加，发送失败不冒充转码失败。 */
    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void videoSchedulerTask() {
        transactions.executeWithoutResult(transaction -> {
            // 超过转码超时再增加两分钟宽限，恢复进程退出留下的处理中记录。
            tasks.markStalledFilesFailed(ffmpegConfig.getTimeoutMinutes() + 2);
            tasks.markStalledProcessesFailed(ffmpegConfig.getTimeoutMinutes() + 2);
        });
        // 取消任务超出最大运行时长时清除运行标记，绝不把删除中的文件恢复成失败或待处理。
        tasks.cancelDeletedProcesses(ffmpegConfig.getTimeoutMinutes() + 2);
        List<Long> ids = tasks.selectDispatchableTasks(
            Math.max(1, maxAttempts),
            Math.max(1, batchSize)
        );
        for (long id : ids) {
            Boolean claimed = transactions.execute(transaction -> {
                int updated = tasks.claimDispatch(id, Math.max(1, maxAttempts));
                if (updated == 1) tasks.markDispatchedFileWaiting(id);
                return updated == 1;
            });
            if (!Boolean.TRUE.equals(claimed)) continue;
            MediaProcess task = tasks.selectById(id);
            if (task == null) continue;
            TranscodeMessageDto message = new TranscodeMessageDto();
            message.setFileMd5(task.getFileId());
            message.setFilename(task.getFilename());
            message.setBucket(task.getBucket());
            message.setFilePath(task.getFilePath());
            try {
                sender.send("video", message);
            } catch (Exception error) {
                tasks.markDispatchFailure(id);
                log.warn("转码消息等待恢复：{}", id, error);
            }
        }
    }
}
