package com.sunflower_class.service;

import com.sunflower_class.model.dto.UploadFileParamsDto;
import com.sunflower_class.model.po.MediaFiles;

public interface AddMediaFilesService {
    public MediaFiles addMediaFiles(Long companyId, String md5, String bucketName, String objectName,
            UploadFileParamsDto uploadFileParamsDto);
}