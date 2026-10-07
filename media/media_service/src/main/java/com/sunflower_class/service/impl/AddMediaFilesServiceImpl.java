package com.sunflower_class.service.impl;

import static com.sunflower_class.base.model.BusinessCodes.FILE_VIDEO;
import static com.sunflower_class.base.model.BusinessCodes.MEDIA_AUDIT_APPROVED;
import static com.sunflower_class.base.model.BusinessCodes.PROCESS_READY;
import static com.sunflower_class.base.model.BusinessCodes.PROCESS_WAITING;

import com.sunflower_class.base.course.CourseMessageSender;
import com.sunflower_class.mapper.MediaFilesMapper;
import com.sunflower_class.mapper.MediaProcessMapper;
import com.sunflower_class.model.dto.TranscodeMessageDto;
import com.sunflower_class.model.dto.UploadFileParamsDto;
import com.sunflower_class.model.po.MediaFiles;
import com.sunflower_class.model.po.MediaProcess;
import com.sunflower_class.service.AddMediaFilesService;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 将上传结果登记到数据库，区分普通文件与视频，并安排提交后的转码消息。
 */
@Slf4j
@Service
public class AddMediaFilesServiceImpl implements AddMediaFilesService {

    @Autowired
    private MediaFilesMapper mediaFilesMapper;

    @Autowired
    private MediaProcessMapper mediaProcessMapper;

    @Autowired
    private CourseMessageSender sender;

    /**
     * 保存媒资元数据与存储位置；视频登记待转码任务，普通素材标记为可用。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MediaFiles addMediaFiles(
        Long companyId,
        String md5,
        String bucketName,
        String objectName,
        UploadFileParamsDto uploadFileParamsDto
    ) {
        if (
            StringUtils.isBlank(md5) ||
            StringUtils.isBlank(objectName) ||
            uploadFileParamsDto == null
        ) {
            log.error(
                "入参不合法: md5={}, objectName={}, dto={}",
                md5,
                objectName,
                uploadFileParamsDto
            );
            return null;
        }

        log.info(
            "开始保存文件记录: companyId={}, md5={}, bucketName={}, objectName={}",
            companyId,
            md5,
            bucketName,
            objectName
        );

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
        mediaFiles.setStatus(
            FILE_VIDEO.equals(mediaFiles.getFileType()) ? PROCESS_WAITING : PROCESS_READY
        );
        mediaFiles.setAuditStatus(MEDIA_AUDIT_APPROVED);

        int insert = mediaFilesMapper.insert(mediaFiles);
        if (insert <= 0) {
            log.error(
                "保存文件记录失败: md5={}, fileName={}",
                md5,
                uploadFileParamsDto.getFilename()
            );
            return null;
        }

        addWaitingTask(mediaFiles);

        log.info("文件记录保存成功: md5={}, fileName={}", md5, uploadFileParamsDto.getFilename());
        return mediaFiles;
    }

    /**
     * 仅对视频创建待处理记录，并注册事务提交后的消息发送。
     */
    private void addWaitingTask(MediaFiles mediaFiles) {
        String filename = mediaFiles.getFilename();
        String fileExtension = getFileExtension(filename);
        String mimeType = getMimeType(fileExtension);

        if (mimeType.startsWith("video/")) {
            MediaProcess mediaProcess = new MediaProcess();
            BeanUtils.copyProperties(mediaFiles, mediaProcess);

            mediaProcess.setStatus(PROCESS_WAITING);
            mediaProcess.setFailCount(0);
            mediaProcessMapper.insert(mediaProcess);

            log.info("视频转码任务已添加: fileId={}", mediaFiles.getId());

            sendMessage(mediaFiles);
        }
        return;
    }

    /**
     * 根据扩展名推断 MIME 类型；无法识别时返回通用二进制类型。
     */
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

    /**
     * 提取最后一个点之后的文件扩展名并转成小写，空文件名或无扩展名时返回空字符串。
     */
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

    /**
     * 注册事务同步回调，提交成功后向视频队列发送文件位置，发送失败时记录日志。
     */
    private void sendMessage(MediaFiles mediaFiles) {
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                /**
                 * 数据库事务提交成功后发送转码任务，避免消费者早于任务记录落库开始处理。
                 */
                @Override
                public void afterCommit() {
                    try {
                        TranscodeMessageDto message = new TranscodeMessageDto();
                        message.setFileMd5(mediaFiles.getId());
                        message.setFilename(mediaFiles.getFilename());
                        message.setBucket(mediaFiles.getBucket());
                        message.setFilePath(mediaFiles.getFilePath());

                        sender.send("video", message);
                        log.info("转码消息已发送: fileMd5={}", mediaFiles.getId());
                    } catch (Exception e) {
                        log.error("发送转码消息失败: fileId={}", mediaFiles.getId(), e);
                    }
                }
            }
        );
    }
}
