package com.sunflower_class.service.impl;

import java.time.LocalDateTime;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sunflower_class.mapper.MediaFilesMapper;
import com.sunflower_class.model.dto.UploadFileParamsDto;
import com.sunflower_class.model.po.MediaFiles;
import com.sunflower_class.service.AddMediaFilesService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AddMediaFilesServiceImpl implements AddMediaFilesService {
    @Autowired
    MediaFilesMapper mediaFilesMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MediaFiles addMediaFiles(Long companyId, String md5, String bucketName, String objectName,
            UploadFileParamsDto uploadFileParamsDto) {

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

        log.info("文件记录保存成功: md5={}, fileName={}", md5, uploadFileParamsDto.getFilename());
        return mediaFiles;
    }
}
