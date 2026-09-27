package com.sunflower_class.service;

import com.sunflower_class.base.model.PageParams;
import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.base.model.RestResponse;
import com.sunflower_class.model.dto.QueryMediaParamsDto;
import com.sunflower_class.model.dto.UploadFileParamsDto;
import com.sunflower_class.model.dto.UploadFileResultDto;
import com.sunflower_class.model.po.MediaFiles;
import org.springframework.web.multipart.MultipartFile;

/**
 * 普通文件与视频分片上传的业务契约，同时提供媒资分页查询。
 */
public interface MediaFileService {
    /**
     * 按机构和查询条件分页读取媒资记录，返回当前页及总条数。
     */
    public PageResult<MediaFiles> queryMediaFiels(
        Long companyId,
        PageParams pageParams,
        QueryMediaParamsDto queryMediaParamsDto
    );

    /**
     * 上传普通文件并校验完整性，保存当前机构的媒资记录；不同机构的同内容文件不复用归属记录。
     */
    UploadFileResultDto uploadFiles(
        Long companyId,
        MultipartFile file,
        UploadFileParamsDto uploadFileParamsDto
    );

    /**
     * 依据文件 MD5 查找元数据并检查存储对象；不存在返回 false，存储异常返回业务错误。
     */
    public RestResponse<Boolean> checkFile(String fileMd5);

    /**
     * 依据整文件 MD5 和分片序号检查对象是否存在，供断点续传跳过已上传分片。
     */
    public RestResponse<Boolean> checkChunk(String fileMd5, int chunkIndex);

    /**
     * 将单个分片保存到 MD5 对应的目录，供后续按分片顺序合并。
     */
    public RestResponse uploadChunk(String fileMd5, int chunk, MultipartFile file);

    /**
     * 检查并合并视频分片，校验整文件 MD5 后保存媒资元数据和转码任务，再清理分片。
     */
    public RestResponse mergechunks(
        Long companyId,
        String fileMd5,
        int chunkTotal,
        UploadFileParamsDto uploadFileParamsDto
    );
}
