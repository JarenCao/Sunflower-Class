package com.sunflower_class.service;

import com.sunflower_class.model.dto.UploadFileParamsDto;
import com.sunflower_class.model.po.MediaFiles;

/**
 * 媒资元数据保存及转码任务登记的业务契约。
 */
public interface AddMediaFilesService {
    /**
     * 保存媒资元数据与存储位置；视频登记待转码任务，普通素材标记为可用。
     */
    public MediaFiles addMediaFiles(
        Long companyId,
        String md5,
        String bucketName,
        String objectName,
        UploadFileParamsDto uploadFileParamsDto
    );
}
