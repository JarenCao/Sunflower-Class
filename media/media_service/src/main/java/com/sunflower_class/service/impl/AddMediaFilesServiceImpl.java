package com.sunflower_class.service.impl;

import java.time.LocalDateTime;

import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.sunflower_class.mapper.MediaFilesMapper;
import com.sunflower_class.mapper.MediaProcessMapper;
import com.sunflower_class.model.dto.TranscodeMessageDto;
import com.sunflower_class.model.dto.UploadFileParamsDto;
import com.sunflower_class.model.po.MediaFiles;
import com.sunflower_class.model.po.MediaProcess;
import com.sunflower_class.service.AddMediaFilesService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AddMediaFilesServiceImpl implements AddMediaFilesService {

    @Autowired
    private MediaFilesMapper mediaFilesMapper;

    @Autowired
    private MediaProcessMapper mediaProcessMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MediaFiles addMediaFiles(Long companyId, String md5, String bucketName, String objectName,
            UploadFileParamsDto uploadFileParamsDto) {

        if (StringUtils.isBlank(md5) || StringUtils.isBlank(objectName)
                || uploadFileParamsDto == null) {
            log.error("入参不合法: md5={}, objectName={}, dto={}", md5, objectName, uploadFileParamsDto);
            return null;
        }

        log.info("开始保存文件记录: companyId={}, md5={}, bucketName={}, objectName={}",
                companyId, md5, bucketName, objectName);

        MediaFiles mediaFiles = mediaFilesMapper.selectById(md5);
        if (mediaFiles != null) {
            log.info("文件记录已存在: md5={}, fileName={}", md5, mediaFiles.getFilename());
            return mediaFiles;
        }

        mediaFiles = new MediaFiles();
        BeanUtils.copyProperties(uploadFileParamsDto, mediaFiles);

        mediaFiles.setId(md5);
        mediaFiles.setCompanyId(companyId);
        mediaFiles.setBucket(bucketName);
        mediaFiles.setFilePath(objectName);
        mediaFiles.setFileId(md5);
        mediaFiles.setUrl("/" + bucketName + "/" + objectName);
        mediaFiles.setCreateDate(LocalDateTime.now());
        mediaFiles.setStatus("1");
        mediaFiles.setAuditStatus("20203");

        int insert = mediaFilesMapper.insert(mediaFiles);
        if (insert <= 0) {
            log.error("保存文件记录失败: md5={}, fileName={}", md5, uploadFileParamsDto.getFilename());
            return null;
        }

        addWaitingTask(mediaFiles);

        log.info("文件记录保存成功: md5={}, fileName={}", md5, uploadFileParamsDto.getFilename());
        return mediaFiles;
    }

    private void addWaitingTask(MediaFiles mediaFiles) {

        String filename = mediaFiles.getFilename();
        String fileExtension = getFileExtension(filename);
        String mimeType = getMimeType(fileExtension);

        if (mimeType.startsWith("video/")) {
            MediaProcess mediaProcess = new MediaProcess();
            BeanUtils.copyProperties(mediaFiles, mediaProcess);

            mediaProcess.setStatus("1");
            mediaProcess.setFailCount(0);
            mediaProcessMapper.insert(mediaProcess);

            log.info("视频转码任务已添加: fileId={}", mediaFiles.getId());

            sendMessage(mediaFiles);
        }
        return;
    }

    private String getMimeType(String extension) {
        if (StringUtils.isBlank(extension)) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        String mimeType = MediaTypeFactory.getMediaType("file." + extension.toLowerCase())
                .map(MediaType::toString)
                .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        log.debug("获取MIME类型: extension={}, mimeType={}", extension, mimeType);
        return mimeType;
    }

    private String getFileExtension(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return "";
        }
        int lastIndexOf = fileName.lastIndexOf('.');
        if (lastIndexOf == -1) {
            return "";
        }
        String extension = fileName.substring(lastIndexOf + 1).toLowerCase();
        log.debug("获取文件扩展名: fileName={}, extension={}", fileName, extension);
        return extension;
    }

    private void sendMessage(MediaFiles mediaFiles) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    TranscodeMessageDto message = new TranscodeMessageDto();
                    message.setFileMd5(mediaFiles.getId());
                    message.setFilename(mediaFiles.getFilename());
                    message.setBucket(mediaFiles.getBucket());
                    message.setFilePath(mediaFiles.getFilePath());

                    rabbitTemplate.convertAndSend("video.queue", message);
                    log.info("转码消息已发送: fileMd5={}", mediaFiles.getId());
                } catch (Exception e) {
                    log.error("发送转码消息失败: fileId={}", mediaFiles.getId(), e);
                }
            }
        });
    }
}
