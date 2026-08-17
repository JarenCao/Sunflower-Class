package com.sunflower_class.service.impl;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunflower_class.base.exception.GlobalException;
import com.sunflower_class.base.model.PageParams;
import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.base.model.RestResponse;
import com.sunflower_class.config.MinioConfig;
import com.sunflower_class.mapper.MediaFilesMapper;
import com.sunflower_class.model.dto.QueryMediaParamsDto;
import com.sunflower_class.model.dto.UploadFileParamsDto;
import com.sunflower_class.model.dto.UploadFileResultDto;
import com.sunflower_class.model.po.MediaFiles;
import com.sunflower_class.service.AddMediaFilesService;
import com.sunflower_class.service.MediaFileService;

import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.RemoveObjectsArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class MediaFileServiceImpl implements MediaFileService {

    @Autowired
    private MediaFilesMapper mediaFilesMapper;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private MinioConfig minioConfig;

    @Autowired
    private AddMediaFilesService addMediaFilesService;

    @Override
    public PageResult<MediaFiles> queryMediaFiels(Long companyId, PageParams pageParams,
            QueryMediaParamsDto queryMediaParamsDto) {

        if (pageParams == null) {
            pageParams = new PageParams();
        }
        if (queryMediaParamsDto == null) {
            queryMediaParamsDto = new QueryMediaParamsDto();
        }
        log.info("开始分页查询媒资文件, companyId={}, pageNo={}, pageSize={}",
                companyId, pageParams.getPageNo(), pageParams.getPageSize());

        LambdaQueryWrapper<MediaFiles> queryWrapper = new LambdaQueryWrapper<>();

        queryWrapper.like(StringUtils.isNotBlank(queryMediaParamsDto.getFilename()),
                MediaFiles::getFilename, queryMediaParamsDto.getFilename());

        queryWrapper.eq(StringUtils.isNotBlank(queryMediaParamsDto.getAuditStatus()),
                MediaFiles::getAuditStatus, queryMediaParamsDto.getAuditStatus());

        queryWrapper.eq(StringUtils.isNotBlank(queryMediaParamsDto.getFileType()),
                MediaFiles::getFileType, queryMediaParamsDto.getFileType());

        Page<MediaFiles> page = new Page<>(pageParams.getPageNo(), pageParams.getPageSize());
        Page<MediaFiles> pageResult = mediaFilesMapper.selectPage(page, queryWrapper);

        PageResult<MediaFiles> mediaListResult = new PageResult<>(
                pageResult.getRecords(),
                pageResult.getTotal(),
                pageParams.getPageNo(),
                pageParams.getPageSize());

        log.info("分页查询媒资文件完成, 总记录数={}, 当前页记录数={}",
                pageResult.getTotal(), pageResult.getRecords().size());
        return mediaListResult;
    }

    @Override
    public UploadFileResultDto uploadFiles(Long companyId, MultipartFile file,
            UploadFileParamsDto uploadFileParamsDto) {

        log.info("开始上传文件: companyId={}, fileName={}, fileSize={}",
                companyId, file.getOriginalFilename(), file.getSize());

        if (file == null || file.isEmpty()) {
            log.error("文件为空");
            throw new IllegalArgumentException("文件不能为空");
        }
        if (uploadFileParamsDto == null) {
            log.error("文件参数为空");
            throw new IllegalArgumentException("文件参数不能为空");
        }
        if (StringUtils.isBlank(uploadFileParamsDto.getFilename())) {
            log.error("文件名称为空");
            throw new IllegalArgumentException("文件名称不能为空");
        }

        String fileName = uploadFileParamsDto.getFilename();
        String fileExtension = getFileExtension(fileName);
        String mimeType = getMimeType(fileExtension);
        String bucketName = getBucketName(mimeType);

        String fileMd5;

        try (InputStream inputStream = file.getInputStream()) {
            fileMd5 = DigestUtils.md5Hex(inputStream);
            log.info("文件MD5计算完成: fileName={}, md5={}", fileName, fileMd5);
        } catch (Exception e) {
            log.error("计算MD5失败: fileName={}", fileName, e);
            throw new RuntimeException("计算MD5失败: " + e.getMessage(), e);
        }

        MediaFiles existingMediaFiles = mediaFilesMapper.selectById(fileMd5);
        if (existingMediaFiles != null) {
            String bucket = existingMediaFiles.getBucket();
            String filePath = existingMediaFiles.getFilePath();

            try {
                minioClient.statObject(
                        StatObjectArgs.builder()
                                .bucket(bucket)
                                .object(filePath)
                                .build());

                log.info("文件已存在: fileMd5={}, fileName={}", fileMd5, fileName);

                UploadFileResultDto result = new UploadFileResultDto();
                BeanUtils.copyProperties(existingMediaFiles, result);
                return result;

            } catch (ErrorResponseException e) {
                if (e.errorResponse().code().equals("NoSuchKey")) {
                    log.warn("数据库记录存在但MinIO文件不存在，删除记录并重新上传: fileMd5={}", fileMd5);
                    mediaFilesMapper.deleteById(fileMd5);
                } else {
                    log.error("检查MinIO文件失败: fileMd5={}", fileMd5, e);
                }
            } catch (Exception e) {
                log.error("检查MinIO文件失败: fileMd5={}", fileMd5, e);
            }
        }

        String objectName = getObjectName(fileName, fileMd5);

        log.info("上传参数: fileName={}, fileExtension={}, mimeType={}, bucketName={}, objectName={}",
                fileName, fileExtension, mimeType, bucketName, objectName);

        try {
            try (InputStream inputStream = file.getInputStream()) {
                uploadFileToMinio(inputStream, bucketName, objectName, mimeType, file.getSize());
                log.info("文件上传到MinIO完成: fileName={}, bucketName={}, objectName={}",
                        fileName, bucketName, objectName);
            }

            if (!verifyFileIntegrityByStat(bucketName, objectName, fileMd5)) {
                log.error("文件完整性校验失败: fileName={}, expectedMd5={}", fileName, fileMd5);
                throw new RuntimeException("文件完整性校验失败");
            }
            log.info("文件完整性校验通过: fileName={}, md5={}", fileName, fileMd5);

            MediaFiles mediaFiles = addMediaFilesService.addMediaFiles(companyId, fileMd5, bucketName, objectName,
                    uploadFileParamsDto);
            if (mediaFiles == null) {
                log.error("保存文件记录失败: fileName={}", fileName);
                GlobalException.cast("保存文件记录失败");
            }

            UploadFileResultDto result = new UploadFileResultDto();

            BeanUtils.copyProperties(mediaFiles, result);

            log.info("文件上传完成: fileName={}, fileId={}, url={}",
                    fileName, mediaFiles.getId(), mediaFiles.getUrl());

            return result;

        } catch (Exception e) {
            log.error("文件上传失败: fileName={}", uploadFileParamsDto.getFilename(), e);
            cleanMinioFile(bucketName, objectName);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public RestResponse<Boolean> checkFile(String fileMd5) {
        MediaFiles mediaFiles = mediaFilesMapper.selectById(fileMd5);
        if (mediaFiles == null) {
            log.info("文件记录不存在: fileMd5={}", fileMd5);
            return RestResponse.success(false);
        }

        String bucket = mediaFiles.getBucket();
        String filePath = mediaFiles.getFilePath();

        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(filePath)
                            .build());
            log.info("文件存在: fileMd5={}", fileMd5);
            return RestResponse.success(true);
        } catch (Exception e) {
            log.error("检查文件失败: fileMd5={}", fileMd5, e);
        }
        return RestResponse.error("检查文件失败");
    }

    @Override
    public RestResponse<Boolean> checkChunk(String fileMd5, int chunkIndex) {

        String chunkFilePath = getChunkFileFolderPath(fileMd5) + chunkIndex;
        String bucket = minioConfig.getVideofiles();

        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(chunkFilePath)
                            .build());
            log.info("分块存在: fileMd5={}, chunk={}", fileMd5, chunkIndex);
            return RestResponse.success(true);
        } catch (Exception e) {
            log.error("检查分块失败: fileMd5={}, chunk={}", fileMd5, chunkIndex, e);
        }
        return RestResponse.error("检查分块失败");
    }

    @Override
    public RestResponse uploadChunk(String fileMd5, int chunk, MultipartFile file) {

        log.info("开始上传分块文件: fileMd5={}, chunk={}, fileName={}, fileSize={}",
                fileMd5, chunk, file.getOriginalFilename(), file.getSize());

        try {
            String chunkFilePath = getChunkFileFolderPath(fileMd5) + chunk;
            String bucket = minioConfig.getVideofiles();
            String mimeType = MediaType.APPLICATION_OCTET_STREAM_VALUE;

            String chunkMd5;
            try (InputStream inputStream = file.getInputStream()) {
                chunkMd5 = DigestUtils.md5Hex(inputStream);
                log.info("分块文件MD5计算完成: fileMd5={}, chunk={}, md5={}", fileMd5, chunk, chunkMd5);
            }

            try (InputStream inputStream = file.getInputStream()) {
                uploadFileToMinio(inputStream, bucket, chunkFilePath, mimeType, file.getSize());
                log.info("分块文件上传成功: fileMd5={}, chunk={}, chunkFilePath={}",
                        fileMd5, chunk, chunkFilePath);
            }

            if (!verifyFileIntegrityByStat(bucket, chunkFilePath, chunkMd5)) {
                log.error("分块文件上传后校验失败: fileMd5={}, chunk={}", fileMd5, chunk);
                cleanMinioFile(bucket, chunkFilePath);
                return RestResponse.error("分块文件上传校验失败");
            }

            log.info("分块文件上传完成: fileMd5={}, chunk={}", fileMd5, chunk);
            return RestResponse.success(true);

        } catch (Exception e) {
            log.error("上传分块文件失败: fileMd5={}, chunk={}", fileMd5, chunk, e);
            return RestResponse.error("上传分块失败: " + e.getMessage());
        }
    }

    @Override
    public RestResponse mergechunks(Long companyId, String fileMd5, int chunkTotal,
            UploadFileParamsDto uploadFileParamsDto) {

        log.info("开始合并分块文件: companyId={}, fileMd5={}, chunkTotal={}, fileName={}",
                companyId, fileMd5, chunkTotal, uploadFileParamsDto.getFilename());

        if (StringUtils.isBlank(fileMd5)) {
            log.error("文件MD5为空");
            return RestResponse.error("文件MD5不能为空");
        }
        if (chunkTotal <= 0) {
            log.error("分块总数无效: chunkTotal={}", chunkTotal);
            return RestResponse.error("分块总数无效");
        }
        if (uploadFileParamsDto == null || StringUtils.isBlank(uploadFileParamsDto.getFilename())) {
            log.error("文件参数或文件名为空");
            return RestResponse.error("文件参数不能为空");
        }

        String bucket = minioConfig.getVideofiles();
        String chunkFileFolderPath = getChunkFileFolderPath(fileMd5);

        try {
            List<ComposeSource> sources = Stream.iterate(0, i -> ++i)
                    .limit(chunkTotal)
                    .map(i -> ComposeSource.builder()
                            .bucket(bucket)
                            .object(chunkFileFolderPath + i)
                            .build())
                    .collect(Collectors.toList());

            log.info("构建合并源完成: 共{}个分块", sources.size());

            String filename = uploadFileParamsDto.getFilename();
            String mergeFilePath = getObjectName(filename, fileMd5);

            log.info("合并文件路径: bucket={}, object={}", bucket, mergeFilePath);

            try {
                minioClient.statObject(
                        StatObjectArgs.builder()
                                .bucket(bucket)
                                .object(mergeFilePath)
                                .build());
                log.info("合并文件已存在，跳过合并: fileMd5={}", fileMd5);

                MediaFiles mediaFiles = addMediaFilesService.addMediaFiles(
                        companyId, fileMd5, bucket, mergeFilePath, uploadFileParamsDto);
                if (mediaFiles != null) {
                    cleanChunkFiles(bucket, chunkFileFolderPath, chunkTotal);
                    return RestResponse.success(true);
                }
            } catch (ErrorResponseException e) {
                if (!e.errorResponse().code().equals("NoSuchKey")) {
                    log.warn("检查合并文件是否存在出错: {}", mergeFilePath, e);
                }

            } catch (Exception e) {
                log.warn("检查合并文件是否存在出错: {}", mergeFilePath, e);
            }

            ObjectWriteResponse response = minioClient.composeObject(
                    ComposeObjectArgs.builder()
                            .bucket(bucket)
                            .object(mergeFilePath)
                            .sources(sources)
                            .build());

            log.info("合并文件成功: {}, etag={}", mergeFilePath, response.etag());

            if (!verifyFileIntegrityByStat(bucket, mergeFilePath, fileMd5)) {
                log.error("合并后文件校验失败: fileMd5={}", fileMd5);
                cleanMinioFile(bucket, mergeFilePath);
                return RestResponse.error("合并后文件校验失败");
            }
            log.info("合并后文件校验通过: fileMd5={}", fileMd5);

            MediaFiles mediaFiles = addMediaFilesService.addMediaFiles(
                    companyId,
                    fileMd5,
                    bucket,
                    mergeFilePath,
                    uploadFileParamsDto);

            if (mediaFiles == null) {
                log.error("保存文件记录失败: fileMd5={}", fileMd5);
                cleanMinioFile(bucket, mergeFilePath);
                return RestResponse.error("保存文件记录失败");
            }

            cleanChunkFiles(bucket, chunkFileFolderPath, chunkTotal);

            log.info("分块文件合并完成: fileMd5={}, fileId={}, url={}",
                    fileMd5, mediaFiles.getId(), mediaFiles.getUrl());

            return RestResponse.success(true);

        } catch (Exception e) {
            log.error("合并文件失败: fileMd5={}, chunkTotal={}", fileMd5, chunkTotal, e);
            return RestResponse.error("合并文件失败: " + e.getMessage());
        }
    }

    private boolean verifyFileIntegrityByStat(String bucket, String objectName, String expectedMd5) {
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build());

            String etag = stat.etag().replace("\"", "");
            log.debug("获取文件信息: bucket={}, objectName={}, size={}, etag={}",
                    bucket, objectName, stat.size(), etag);

            if (stat.size() <= 0) {
                log.error("文件大小为0: bucket={}, objectName={}", bucket, objectName);
                return false;
            }

            if (etag.contains("-")) {
                log.info("文件是分片上传的（size={}），跳过MD5校验: bucket={}, objectName={}",
                        stat.size(), bucket, objectName);
                return true;
            }

            boolean match = etag.equalsIgnoreCase(expectedMd5);
            if (match) {
                log.info("文件MD5校验通过: bucket={}, objectName={}", bucket, objectName);
            } else {
                log.error("文件MD5校验失败: bucket={}, objectName={}, expected={}, actual={}",
                        bucket, objectName, expectedMd5, etag);
            }
            return match;

        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                log.warn("文件不存在: bucket={}, objectName={}", bucket, objectName);
            } else {
                log.error("MinIO错误: bucket={}, objectName={}", bucket, objectName, e);
            }
            return false;
        } catch (Exception e) {
            log.error("校验文件失败: bucket={}, objectName={}", bucket, objectName, e);
            return false;
        }
    }

    private void cleanChunkFiles(String bucket, String chunkFileFolderPath, int chunkTotal) {
        log.info("开始清理分块文件: bucket={}, path={}, total={}", bucket, chunkFileFolderPath, chunkTotal);

        try {
            List<DeleteObject> deleteObjects = Stream.iterate(0, i -> ++i)
                    .limit(chunkTotal)
                    .map(i -> new DeleteObject(chunkFileFolderPath + i))
                    .collect(Collectors.toList());

            RemoveObjectsArgs removeObjectsArgs = RemoveObjectsArgs.builder()
                    .bucket(bucket)
                    .objects(deleteObjects)
                    .build();

            Iterable<Result<DeleteError>> results = minioClient.removeObjects(removeObjectsArgs);

            int successCount = 0;
            int failCount = 0;

            for (Result<DeleteError> result : results) {
                try {
                    DeleteError deleteError = result.get();
                    if (deleteError != null) {
                        failCount++;
                        log.error("清理分块文件失败: objectName={}, errorCode={}, errorMessage={}",
                                deleteError.objectName(),
                                deleteError.code(),
                                deleteError.message());
                    } else {
                        successCount++;
                    }
                } catch (Exception e) {
                    failCount++;
                    log.error("处理删除结果异常", e);
                }
            }

            log.info("分块文件清理完成: 成功={}, 失败={}, 总计={}", successCount, failCount, chunkTotal);

            if (failCount > 0) {
                log.warn("部分分块文件清理失败，需要人工处理: bucket={}, path={}", bucket, chunkFileFolderPath);
            }

        } catch (Exception e) {
            log.error("清理分块文件失败: bucket={}, chunkFileFolderPath={}", bucket, chunkFileFolderPath, e);
        }
    }

    private void cleanMinioFile(String bucketName, String objectName) {
        if (bucketName == null || objectName == null) {
            log.warn("桶名或对象名为空，跳过清理");
            return;
        }
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build());
            log.info("已清理MinIO文件: {}/{}", bucketName, objectName);
        } catch (Exception ex) {
            log.error("清理MinIO文件失败，需要人工处理: {}/{}", bucketName, objectName, ex);
        }
    }

    private String getChunkFileFolderPath(String fileMd5) {
        return fileMd5.substring(0, 1) + "/" + fileMd5.substring(1, 2) +
                "/" + fileMd5 + "/" + "chunk" + "/";
    }

    private void uploadFileToMinio(InputStream inputStream, String bucket, String objectName,
            String mimeType, long fileSize) {

        log.info("开始上传文件到MinIO: bucket={}, objectName={}, fileSize={}",
                bucket, objectName, fileSize);

        try {
            PutObjectArgs args = PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(inputStream, fileSize, -1)
                    .contentType(mimeType)
                    .build();

            minioClient.putObject(args);

            log.info("文件上传到MinIO成功: bucket={}, objectName={}", bucket, objectName);

        } catch (Exception e) {
            log.error("上传文件到MinIO失败: bucket={}, objectName={}", bucket, objectName, e);
            throw new RuntimeException("上传到MinIO失败: " + e.getMessage(), e);
        }
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

    private String getBucketName(String mimeType) {
        String bucket = mimeType.startsWith("video/")
                ? minioConfig.getVideofiles()
                : minioConfig.getFiles();
        log.debug("根据MIME类型获取桶: mimeType={}, bucket={}", mimeType, bucket);
        return bucket;
    }

    private String getObjectName(String originalFilename, String fileMd5) {
        LocalDate now = LocalDate.now();
        String year = String.valueOf(now.getYear());
        String monthDay = now.format(DateTimeFormatter.ofPattern("MM-dd"));

        String fileName = fileMd5 + "_" + originalFilename;

        String objectName = year + "/" + monthDay + "/" + fileName;
        log.debug("生成存储路径: objectName={}", objectName);
        return objectName;
    }
}