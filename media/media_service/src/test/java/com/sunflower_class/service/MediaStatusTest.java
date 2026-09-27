package com.sunflower_class.service;

import static com.sunflower_class.base.model.BusinessCodes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.rabbitmq.client.Channel;
import com.sunflower_class.mapper.*;
import com.sunflower_class.model.po.*;
import com.sunflower_class.service.impl.ConsumerServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 转码成功归档、失败状态及配置重试次数的回归测试。
 */
class MediaStatusTest {

    /**
     * 验证转码成功会更新媒资状态，归档任务并使用五位状态编码。
     */
    @Test
    void successUpdatesFileAndArchivesFiveDigitStatus() {
        var files = mock(MediaFilesMapper.class);
        var tasks = mock(MediaProcessMapper.class);
        var history = mock(MediaProcessHistoryMapper.class);
        var service = new ConsumerServiceImpl();
        ReflectionTestUtils.setField(service, "mediaFilesMapper", files);
        ReflectionTestUtils.setField(service, "mediaProcessMapper", tasks);
        ReflectionTestUtils.setField(service, "mediaProcessHistoryMapper", history);
        var file = new MediaFiles();
        file.setId("test");
        file.setStatus(PROCESS_RUNNING);
        var task = new MediaProcess();
        task.setId(1L);
        task.setFileId("test");
        when(files.selectById("test")).thenReturn(file);
        when(tasks.selectOne(any())).thenReturn(task);
        service.markAsSuccess("test", "configured-video-bucket", "transcoded/test.mp4");
        assertEquals(PROCESS_READY, file.getStatus());
        assertEquals("configured-video-bucket", file.getBucket());
        assertEquals(PROCESS_READY, task.getStatus());
        verify(history).insert(any(MediaProcessHistory.class));
        verify(tasks).deleteById(1L);
    }

    /**
     * 验证失败次数达到配置上限时停止重试，并同步媒资列表可见状态。
     */
    @Test
    void failureHonorsConfiguredAttemptLimitAndUpdatesVisibleStatus() throws Exception {
        var files = mock(MediaFilesMapper.class);
        var tasks = mock(MediaProcessMapper.class);
        var channel = mock(Channel.class);
        var service = new ConsumerServiceImpl();
        ReflectionTestUtils.setField(service, "mediaFilesMapper", files);
        ReflectionTestUtils.setField(service, "mediaProcessMapper", tasks);
        ReflectionTestUtils.setField(service, "maxAttempts", 2);
        var file = new MediaFiles();
        file.setId("test");
        var task = new MediaProcess();
        task.setId(1L);
        task.setFailCount(1);
        when(files.selectById("test")).thenReturn(file);
        when(tasks.selectOne(any())).thenReturn(task);
        service.handleFailure("test", "transcode failed", 9L, channel);
        assertEquals(PROCESS_FAILED, file.getStatus());
        assertEquals(PROCESS_FAILED, task.getStatus());
        assertEquals(2, task.getFailCount());
        verify(channel).basicAck(9L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }
}
