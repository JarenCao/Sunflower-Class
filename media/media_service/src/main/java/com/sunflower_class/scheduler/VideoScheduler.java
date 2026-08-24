package com.sunflower_class.scheduler;

import java.util.List;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sunflower_class.mapper.MediaProcessMapper;
import com.sunflower_class.model.dto.TranscodeMessageDto;
import com.sunflower_class.model.po.MediaProcess;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class VideoScheduler {

    @Autowired
    private MediaProcessMapper mediaProcessMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Scheduled(cron = "0 */5 * * * ?")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void videoSchedulerTask() {

        List<MediaProcess> failedTasks = mediaProcessMapper.selectShedulerTasks(3, 10);
        if (failedTasks == null || failedTasks.isEmpty()) {
            log.debug("没有超过最大重试次数的失败任务");
            return;
        }

        log.info("查询到 {} 个超过最大重试次数的失败任务", failedTasks.size());

        int count = 0;

        for (MediaProcess mediaProcess : failedTasks) {
            int updated = mediaProcessMapper.updateStatusFailTask(
                    mediaProcess.getId(), 3, 10);

            if (updated > 0) {
                
                publishMessage(mediaProcess);
                
                count++;
                
                log.info("任务已重新发送到MQ: fileId={}, 失败次数={}",
                        mediaProcess.getFileId(), mediaProcess.getFailCount());
            }
        }
        log.info("【定时任务】完成: 成功重新发送 {} 个任务", count);

    }

    public void publishMessage(MediaProcess mediaProcess) {

        TranscodeMessageDto msg = new TranscodeMessageDto();

        msg.setFileMd5(mediaProcess.getFileId());
        msg.setFilename(mediaProcess.getFilename());
        msg.setBucket(mediaProcess.getBucket());
        msg.setFilePath(mediaProcess.getFilePath());

        rabbitTemplate.convertAndSend("direct.exchange", "video", msg);
    }
}
