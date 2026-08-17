package com.sunflower_class.service;

import org.springframework.web.multipart.MultipartFile;

import com.sunflower_class.base.model.PageParams;
import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.base.model.RestResponse;
import com.sunflower_class.model.dto.QueryMediaParamsDto;
import com.sunflower_class.model.dto.UploadFileParamsDto;
import com.sunflower_class.model.dto.UploadFileResultDto;
import com.sunflower_class.model.po.MediaFiles;

public interface MediaFileService {

    public PageResult<MediaFiles> queryMediaFiels(Long companyId, PageParams pageParams,
            QueryMediaParamsDto queryMediaParamsDto);

    UploadFileResultDto uploadFiles(Long companyId, MultipartFile file,
            UploadFileParamsDto uploadFileParamsDto);

    public RestResponse<Boolean> checkFile(String fileMd5);

    public RestResponse<Boolean> checkChunk(String fileMd5, int chunkIndex);

    public RestResponse uploadChunk(String fileMd5, int chunk, MultipartFile file);

    public RestResponse mergechunks(Long companyId, String fileMd5, int chunkTotal,
            UploadFileParamsDto uploadFileParamsDto);
}
