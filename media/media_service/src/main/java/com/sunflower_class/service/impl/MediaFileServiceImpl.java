package com.sunflower_class.service.impl;

import static com.sunflower_class.base.model.BusinessCodes.FILE_IMAGE;
import static com.sunflower_class.base.model.BusinessCodes.FILE_VIDEO;
import static com.sunflower_class.base.model.BusinessCodes.PROCESS_DELETING;
import static com.sunflower_class.base.model.BusinessCodes.PROCESS_READY;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunflower_class.base.exception.GlobalException;
import com.sunflower_class.base.model.PageParams;
import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.base.model.RestResponse;
import com.sunflower_class.base.security.CurrentUser;
import com.sunflower_class.config.MinioConfig;
import com.sunflower_class.mapper.MediaFilesMapper;
import com.sunflower_class.model.dto.PlaybackUrlDto;
import com.sunflower_class.model.dto.QueryMediaParamsDto;
import com.sunflower_class.model.dto.UploadFileParamsDto;
import com.sunflower_class.model.dto.UploadFileResultDto;
import com.sunflower_class.model.po.MediaFiles;
import com.sunflower_class.service.AddMediaFilesService;
import com.sunflower_class.service.MediaDeletionService;
import com.sunflower_class.service.MediaFileService;
import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.RemoveObjectsArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 媒资上传与查询业务实现，协调对象存储、完整性校验、分片清理和数据库登记。
 */
@Slf4j
@Service
public class MediaFileServiceImpl implements MediaFileService {

    @Autowired
    private MediaDeletionService deletion;

    /** 媒资引用检查复用同一删除流程，不在前端推测是否可删除。 */
    @Override
    public Map<String, Long> references(Long companyId, String id) {
        return deletion.references(companyId, id);
    }

    /** 分阶段删除保留失败元数据，重试仍从原有对象路径继续。 */
    @Override
    public boolean deleteMedia(Long companyId, String id) {
        return deletion.delete(companyId, id);
    }

    @Autowired
    private TransactionTemplate taskTransactions;

    /** 任务查询也核对媒资归属，历史成功任务返回完成状态。 */
    @Override
    public Map<String, Object> process(Long companyId, String id) {
        MediaFiles file = mediaFilesMapper.selectById(id);
        if (
            file == null || !companyId.equals(file.getCompanyId())
        ) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "媒资不存在或不属于本机构");
        List<Map<String, Object>> rows = mediaFilesMapper.selectFileProcessStatus(id, id);
        return rows.isEmpty()
            ? Map.of("status", file.getStatus(), "failCount", 0)
            : rows.getFirst();
    }

    /** 行锁与状态条件保证处理中任务不能同时被人工重试，机构编号取自已验签身份。 */
    @Override
    public void retryProcess(Long companyId, String id) {
        taskTransactions.executeWithoutResult(transaction -> {
            List<Map<String, Object>> files = mediaFilesMapper.selectOwnedFileForUpdate(
                id,
                companyId
            );
            if (files.isEmpty()) throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "媒资不存在或不属于本机构"
            );
            if (!"20303".equals(files.getFirst().get("status"))) throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "只有失败媒资可重试，删除中任务不可恢复"
            );
            int updated = mediaFilesMapper.resetFileProcesses(id);
            if (updated != 1) throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "只有失败转码任务可以重新处理"
            );
            mediaFilesMapper.markFileWaiting(id);
        });
    }

    @Autowired
    private DiscoveryClient discoveryClient;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private MinioConfig minioConfig;

    @Autowired
    private MediaFilesMapper mediaFilesMapper;

    @Autowired
    private AddMediaFilesService addMediaFilesService;

    /**
     * 按机构和查询条件分页读取媒资记录，返回当前页及总条数。
     */
    @Override
    public PageResult<MediaFiles> queryMediaFiels(
        Long companyId,
        PageParams pageParams,
        QueryMediaParamsDto queryMediaParamsDto
    ) {
        if (pageParams == null) {
            pageParams = new PageParams();
        }
        if (queryMediaParamsDto == null) {
            queryMediaParamsDto = new QueryMediaParamsDto();
        }
        log.info(
            "开始分页查询媒资文件, companyId={}, pageNo={}, pageSize={}",
            companyId,
            pageParams.getPageNo(),
            pageParams.getPageSize()
        );

        LambdaQueryWrapper<MediaFiles> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(MediaFiles::getCompanyId, companyId);
        queryWrapper.orderByDesc(MediaFiles::getCreateDate);

        queryWrapper.like(
            StringUtils.isNotBlank(queryMediaParamsDto.getFilename()),
            MediaFiles::getFilename,
            queryMediaParamsDto.getFilename()
        );

        queryWrapper.eq(
            StringUtils.isNotBlank(queryMediaParamsDto.getAuditStatus()),
            MediaFiles::getAuditStatus,
            queryMediaParamsDto.getAuditStatus()
        );

        queryWrapper.eq(
            StringUtils.isNotBlank(queryMediaParamsDto.getFileType()),
            MediaFiles::getFileType,
            queryMediaParamsDto.getFileType()
        );

        Page<MediaFiles> page = new Page<>(pageParams.getPageNo(), pageParams.getPageSize());
        Page<MediaFiles> pageResult = mediaFilesMapper.selectPage(page, queryWrapper);

        PageResult<MediaFiles> mediaListResult = new PageResult<>(
            pageResult.getRecords(),
            pageResult.getTotal(),
            pageParams.getPageNo(),
            pageParams.getPageSize()
        );

        log.info(
            "分页查询媒资文件完成, 总记录数={}, 当前页记录数={}",
            pageResult.getTotal(),
            pageResult.getRecords().size()
        );
        return mediaListResult;
    }

    /**
     * 上传普通文件并校验完整性，保存当前机构的媒资记录；不同机构的同内容文件不复用归属记录。
     */
    @Override
    public UploadFileResultDto uploadFiles(
        Long companyId,
        MultipartFile file,
        UploadFileParamsDto uploadFileParamsDto
    ) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件不能为空");
        }
        if (uploadFileParamsDto == null || StringUtils.isBlank(uploadFileParamsDto.getFilename())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件名称不能为空");
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

        String recordId = getRecordId(companyId, fileMd5);
        MediaFiles existingMediaFiles = mediaFilesMapper.selectById(recordId);
        if (existingMediaFiles != null) {
            String bucket = existingMediaFiles.getBucket();
            String filePath = existingMediaFiles.getFilePath();

            try {
                minioClient.statObject(
                    StatObjectArgs.builder().bucket(bucket).object(filePath).build()
                );

                log.info("文件已存在: fileMd5={}, fileName={}", fileMd5, fileName);

                UploadFileResultDto result = new UploadFileResultDto();
                BeanUtils.copyProperties(existingMediaFiles, result);
                return result;
            } catch (ErrorResponseException e) {
                // 记录可能已被课程引用；对象缺失时不能删除原记录或伪装成新上传。
                if ("NoSuchKey".equals(e.errorResponse().code())) {
                    throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "原媒资对象缺失，请先修复原记录"
                    );
                }
                throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "暂时无法检查文件存储"
                );
            } catch (ResponseStatusException rejected) {
                throw rejected;
            } catch (Exception e) {
                throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "暂时无法检查文件存储"
                );
            }
        }
        uploadFileParamsDto.setFileSize(file.getSize());

        String objectName = getObjectName(fileName, recordId);

        log.info(
            "上传参数: fileName={}, fileExtension={}, mimeType={}, bucketName={}, objectName={}",
            fileName,
            fileExtension,
            mimeType,
            bucketName,
            objectName
        );

        try {
            try (InputStream inputStream = file.getInputStream()) {
                uploadFileToMinio(inputStream, bucketName, objectName, mimeType, file.getSize());
                log.info(
                    "文件上传到MinIO完成: fileName={}, bucketName={}, objectName={}",
                    fileName,
                    bucketName,
                    objectName
                );
            }

            if (!verifyFileIntegrityByStat(bucketName, objectName, fileMd5)) {
                log.error("文件完整性校验失败: fileName={}, expectedMd5={}", fileName, fileMd5);
                throw new RuntimeException("文件完整性校验失败");
            }
            log.info("文件完整性校验通过: fileName={}, md5={}", fileName, fileMd5);

            MediaFiles mediaFiles = addMediaFilesService.addMediaFiles(
                companyId,
                recordId,
                bucketName,
                objectName,
                uploadFileParamsDto
            );
            if (mediaFiles == null) {
                log.error("保存文件记录失败: fileName={}", fileName);
                GlobalException.cast("保存文件记录失败");
            }

            UploadFileResultDto result = new UploadFileResultDto();

            BeanUtils.copyProperties(mediaFiles, result);

            log.info(
                "文件上传完成: fileName={}, fileId={}, url={}",
                fileName,
                mediaFiles.getId(),
                mediaFiles.getUrl()
            );

            return result;
        } catch (Exception e) {
            log.error("文件上传失败: fileName={}", uploadFileParamsDto.getFilename(), e);
            cleanMinioFile(bucketName, objectName);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * 依据文件 MD5 查找元数据并检查存储对象；不存在返回 false，存储异常返回业务错误。
     */
    @Override
    public RestResponse<Boolean> checkFile(Long companyId, String fileMd5) {
        validateUpload(fileMd5, 0);
        MediaFiles mediaFiles = mediaFilesMapper.selectById(getRecordId(companyId, fileMd5));
        if (mediaFiles == null) return RestResponse.success(false);
        try {
            return RestResponse.success(
                minioClient
                    .statObject(
                        StatObjectArgs.builder()
                            .bucket(mediaFiles.getBucket())
                            .object(mediaFiles.getFilePath())
                            .build()
                    )
                    .size() > 0
            );
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) return RestResponse.success(false);
            log.error("检查文件存储失败: fileMd5={}", fileMd5, e);
        } catch (Exception e) {
            log.error("检查文件存储失败: fileMd5={}", fileMd5, e);
        }
        return RestResponse.error("检查文件失败，请稍后重试");
    }

    /** 分片路径包含机构标识，其他机构不能检查或覆盖本机构的断点。 */
    @Override
    public RestResponse<Boolean> checkChunk(Long companyId, String fileMd5, int chunkIndex) {
        validateUpload(fileMd5, chunkIndex);
        String path = getChunkFileFolderPath(companyId, fileMd5) + chunkIndex;
        try {
            return RestResponse.success(
                minioClient
                    .statObject(
                        StatObjectArgs.builder()
                            .bucket(minioConfig.getVideofiles())
                            .object(path)
                            .build()
                    )
                    .size() > 0
            );
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) return RestResponse.success(false);
            log.error("检查分片失败: fileMd5={}, chunk={}", fileMd5, chunkIndex, e);
        } catch (Exception e) {
            log.error("检查分片失败: fileMd5={}, chunk={}", fileMd5, chunkIndex, e);
        }
        return RestResponse.error("检查分片失败，请稍后重试");
    }

    /** 上传后核对分片内容；上传异常只清理本次分片，不影响其他可续传分片。 */
    @Override
    public RestResponse uploadChunk(Long companyId, String fileMd5, int chunk, MultipartFile file) {
        validateUpload(fileMd5, chunk);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分片不能为空");
        }
        String bucket = minioConfig.getVideofiles();
        String path = getChunkFileFolderPath(companyId, fileMd5) + chunk;
        try {
            String chunkMd5;
            try (InputStream stream = file.getInputStream()) {
                chunkMd5 = DigestUtils.md5Hex(stream);
            }
            try (InputStream stream = file.getInputStream()) {
                uploadFileToMinio(
                    stream,
                    bucket,
                    path,
                    MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    file.getSize()
                );
            }
            if (!verifyFileIntegrityByStat(bucket, path, chunkMd5)) {
                cleanMinioFile(bucket, path);
                return RestResponse.error("分片校验失败，请重新上传此分片");
            }
            return RestResponse.success(true);
        } catch (Exception e) {
            cleanMinioFile(bucket, path);
            log.error("上传分片失败: fileMd5={}, chunk={}", fileMd5, chunk, e);
            return RestResponse.error("上传分片失败，请重新上传此分片");
        }
    }

    /** 缺片时保留断点；完整性失败时清除损坏分片及合并对象，登记成功后清理分片。 */
    @Override
    public RestResponse mergechunks(
        Long companyId,
        String fileMd5,
        int chunkTotal,
        UploadFileParamsDto params
    ) {
        validateUpload(fileMd5, 0);
        if (chunkTotal <= 0 || chunkTotal > 10000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分片总数应为1至10000");
        }
        if (params == null || StringUtils.isBlank(params.getFilename())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件名称不能为空");
        }
        String bucket = minioConfig.getVideofiles();
        String folder = getChunkFileFolderPath(companyId, fileMd5);
        String recordId = getRecordId(companyId, fileMd5);
        MediaFiles existing = mediaFilesMapper.selectById(recordId);
        if (existing != null) {
            // 视频转码后内容会变化，已登记的有效媒资按记录位置核对，不与原视频MD5比较。
            RestResponse<Boolean> checked = checkFile(companyId, fileMd5);
            if (Boolean.TRUE.equals(checked.getResult())) {
                cleanChunkFiles(bucket, folder, chunkTotal);
                return RestResponse.success(true);
            }
            return RestResponse.error("原媒资对象缺失或存储不可用，请先修复原记录");
        }
        String path = getObjectName(params.getFilename(), recordId);
        boolean merged = false;
        try {
            long totalSize = 0;
            // 合并前显式检查全部分片；缺片只提示续传，不清除已有有效断点。
            for (int i = 0; i < chunkTotal; i++) {
                try {
                    long size = minioClient
                        .statObject(
                            StatObjectArgs.builder()
                                .bucket(bucket)
                                .object(folder + i)
                                .build()
                        )
                        .size();
                    if (size <= 0) return RestResponse.error("分片" + i + "为空，请重新上传此分片");
                    totalSize += size;
                } catch (ErrorResponseException missing) {
                    if ("NoSuchKey".equals(missing.errorResponse().code())) {
                        return RestResponse.error("缺少分片" + i + "，请重新选择原文件续传");
                    }
                    throw missing;
                }
            }
            List<ComposeSource> sources = Stream.iterate(0, i -> i + 1)
                .limit(chunkTotal)
                .map(i ->
                    ComposeSource.builder()
                        .bucket(bucket)
                        .object(folder + i)
                        .build()
                )
                .collect(Collectors.toList());
            minioClient.composeObject(
                ComposeObjectArgs.builder().bucket(bucket).object(path).sources(sources).build()
            );
            merged = true;
            long actualSize = minioClient
                .statObject(StatObjectArgs.builder().bucket(bucket).object(path).build())
                .size();
            if (actualSize != totalSize || !verifyFileIntegrityByStat(bucket, path, fileMd5)) {
                cleanMinioFile(bucket, path);
                cleanChunkFiles(bucket, folder, chunkTotal);
                return RestResponse.error("合并文件校验失败，损坏分片已清理，请重新上传");
            }
            params.setFileSize(actualSize);
            // 原有独立事务同时保存媒资与转码任务；异常回滚后不会留下半条登记记录。
            MediaFiles saved = addMediaFilesService.addMediaFiles(
                companyId,
                recordId,
                bucket,
                path,
                params
            );
            if (saved == null) throw new IllegalStateException("保存媒资记录失败");
            cleanChunkFiles(bucket, folder, chunkTotal);
            return RestResponse.success(true);
        } catch (Exception e) {
            // 登记失败只清理本次合并产物，保留有效分片供重试，不能误删已提交的媒资。
            if (merged && mediaFilesMapper.selectById(recordId) == null) cleanMinioFile(
                bucket,
                path
            );
            log.error("合并文件失败: fileMd5={}, chunkTotal={}", fileMd5, chunkTotal, e);
            return RestResponse.error("合并失败，请稍后重试；已上传分片保留");
        }
    }

    /** 与普通上传共用机构记录标识；仅兼容本机构原MD5记录，不读取其他机构媒资。 */
    private String getRecordId(Long companyId, String fileMd5) {
        MediaFiles legacy = mediaFilesMapper.selectById(fileMd5);
        String id =
            legacy != null && Objects.equals(companyId, legacy.getCompanyId())
                ? fileMd5
                : DigestUtils.md5Hex(companyId + ":" + fileMd5);
        MediaFiles file = mediaFilesMapper.selectById(id);
        if (file != null && !Objects.equals(companyId, file.getCompanyId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "文件记录归属冲突");
        }
        return id;
    }

    /** 校验后再截取路径，避免短MD5、路径字符或负序号造成500或非法存储路径。 */
    private void validateUpload(String fileMd5, int chunk) {
        if (fileMd5 == null || !fileMd5.matches("[0-9a-f]{32}")) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "文件MD5应为32位小写十六进制字符"
            );
        }
        if (chunk < 0 || chunk >= 10000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分片序号应为0至9999");
        }
    }

    /**
     * 检查对象非空并核对 MD5；普通 ETag 直接比较，分段 ETag 则下载对象流重新计算校验值。
     */
    private boolean verifyFileIntegrityByStat(
        String bucket,
        String objectName,
        String expectedMd5
    ) {
        try {
            StatObjectResponse stat = minioClient.statObject(
                StatObjectArgs.builder().bucket(bucket).object(objectName).build()
            );

            String etag = stat.etag().replace("\"", "");
            log.debug(
                "获取文件信息: bucket={}, objectName={}, size={}, etag={}",
                bucket,
                objectName,
                stat.size(),
                etag
            );

            if (stat.size() <= 0) {
                log.error("文件大小为0: bucket={}, objectName={}", bucket, objectName);
                return false;
            }

            if (etag.contains("-")) {
                try (
                    InputStream stream = minioClient.getObject(
                        GetObjectArgs.builder().bucket(bucket).object(objectName).build()
                    )
                ) {
                    return DigestUtils.md5Hex(stream).equalsIgnoreCase(expectedMd5);
                }
            }

            boolean match = etag.equalsIgnoreCase(expectedMd5);
            if (match) {
                log.info("文件MD5校验通过: bucket={}, objectName={}", bucket, objectName);
            } else {
                log.error(
                    "文件MD5校验失败: bucket={}, objectName={}, expected={}, actual={}",
                    bucket,
                    objectName,
                    expectedMd5,
                    etag
                );
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

    /**
     * 批量清理指定文件的分片对象，并对删除过程中的异常进行记录。
     */
    private void cleanChunkFiles(String bucket, String chunkFileFolderPath, int chunkTotal) {
        log.info(
            "开始清理分块文件: bucket={}, path={}, total={}",
            bucket,
            chunkFileFolderPath,
            chunkTotal
        );

        try {
            // 生成本次文件的全部分片序号，再转换为待删除的对象路径。
            List<DeleteObject> deleteObjects = Stream.iterate(0, i -> ++i)
                .limit(chunkTotal)
                .map(i -> new DeleteObject(chunkFileFolderPath + i))
                .collect(Collectors.toList());

            RemoveObjectsArgs removeObjectsArgs = RemoveObjectsArgs.builder()
                .bucket(bucket)
                .objects(deleteObjects)
                .build();

            Iterable<Result<DeleteError>> results = minioClient.removeObjects(removeObjectsArgs);

            int failCount = 0;

            for (Result<DeleteError> result : results) {
                try {
                    DeleteError deleteError = result.get();
                    if (deleteError != null) {
                        failCount++;
                        log.error(
                            "清理分块文件失败: objectName={}, errorCode={}, errorMessage={}",
                            deleteError.objectName(),
                            deleteError.code(),
                            deleteError.message()
                        );
                    }
                } catch (Exception e) {
                    failCount++;
                    log.error("处理删除结果异常", e);
                }
            }

            log.info(
                "分块文件清理完成: 成功={}, 失败={}, 总计={}",
                // 批量删除接口只返回失败项，不能把空结果误记为零个删除成功。
                Math.max(0, chunkTotal - failCount),
                failCount,
                chunkTotal
            );

            if (failCount > 0) {
                log.warn(
                    "部分分块文件清理失败，需要人工处理: bucket={}, path={}",
                    bucket,
                    chunkFileFolderPath
                );
            }
        } catch (Exception e) {
            log.error(
                "清理分块文件失败: bucket={}, chunkFileFolderPath={}",
                bucket,
                chunkFileFolderPath,
                e
            );
        }
    }

    /**
     * 尝试删除指定桶中的对象，用于清理失败上传产生的文件。
     */
    private void cleanMinioFile(String bucketName, String objectName) {
        if (bucketName == null || objectName == null) {
            log.warn("桶名或对象名为空，跳过清理");
            return;
        }
        try {
            minioClient.removeObject(
                RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build()
            );
            log.info("已清理MinIO文件: {}/{}", bucketName, objectName);
        } catch (Exception ex) {
            log.error("清理MinIO文件失败，需要人工处理: {}/{}", bucketName, objectName, ex);
        }
    }

    /**
     * 根据文件 MD5 构造分片目录，使同一文件重选上传时可定位既有分片。
     */
    private String getChunkFileFolderPath(Long companyId, String fileMd5) {
        return (
            "chunks/" +
            companyId +
            "/" +
            fileMd5.substring(0, 1) +
            "/" +
            fileMd5.substring(1, 2) +
            "/" +
            fileMd5 +
            "/" +
            "chunk" +
            "/"
        );
    }

    /**
     * 将输入流按给定长度和 MIME 类型写入对象存储，桶和对象路径由调用方指定。
     */
    private void uploadFileToMinio(
        InputStream inputStream,
        String bucket,
        String objectName,
        String mimeType,
        long fileSize
    ) {
        log.info(
            "开始上传文件到MinIO: bucket={}, objectName={}, fileSize={}",
            bucket,
            objectName,
            fileSize
        );

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
     * 根据 MIME 类型选择视频桶或普通媒资桶，桶名取自配置。
     */
    private String getBucketName(String mimeType) {
        String bucket = mimeType.startsWith("video/")
            ? minioConfig.getVideofiles()
            : minioConfig.getFiles();
        log.debug("根据MIME类型获取桶: mimeType={}, bucket={}", mimeType, bucket);
        return bucket;
    }

    /**
     * 使用日期、文件标识及原文件名构造对象路径，便于按日期组织上传资源。
     */
    private String getObjectName(String originalFilename, String fileMd5) {
        LocalDate now = LocalDate.now();
        String year = String.valueOf(now.getYear());
        String monthDay = now.format(DateTimeFormatter.ofPattern("MM-dd"));

        String fileName = fileMd5 + "_" + originalFilename;

        String objectName = year + "/" + monthDay + "/" + fileName;
        log.debug("生成存储路径: objectName={}", objectName);
        return objectName;
    }

    /** 复用服务发现与原始 JWT；学习服务或存储异常时拒绝签发，不退回公开视频。 */
    @Override
    public PlaybackUrlDto playback(long courseId, long lessonId) {
        CurrentUser.requireRole("student");
        return playback(courseId, lessonId, false);
    }

    @Override
    public PlaybackUrlDto trialPlayback(long courseId, long lessonId) {
        return playback(courseId, lessonId, true);
    }

    /** 试学和正式学习复用对象校验；试学只由Learning正式快照授权。 */
    private PlaybackUrlDto playback(long courseId, long lessonId, boolean trial) {
        List<ServiceInstance> instances = discoveryClient.getInstances("learning-api");
        if (instances.isEmpty()) throw new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "学习服务暂不可用"
        );
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        Map<?, ?> grant;
        try {
            RestClient.RequestHeadersSpec<?> request = RestClient.builder()
                .baseUrl(instances.getFirst().getUri().toString())
                .requestFactory(factory)
                .build()
                .get()
                .uri(
                    trial
                        ? "/learning/trial/{courseId}/{lessonId}"
                        : "/learning/playback/{courseId}/{lessonId}",
                    courseId,
                    lessonId
                );
            if (!trial) request.header(
                "Authorization",
                "Bearer " + CurrentUser.jwt().getTokenValue()
            );
            grant = request.retrieve().body(Map.class);
        } catch (HttpClientErrorException rejected) {
            throw new ResponseStatusException(
                rejected.getStatusCode(),
                "尚未获得学习资格、资格已过期、课程已下架或小节不存在"
            );
        } catch (RestClientException unavailable) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "暂时无法核对播放资格"
            );
        }
        if (
            grant == null ||
            !Long.toString(courseId).equals(String.valueOf(grant.get("courseId"))) ||
            !Long.toString(lessonId).equals(String.valueOf(grant.get("lessonId")))
        ) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "播放资格核对失败");
        String mediaId = String.valueOf(grant.get("mediaId"));
        MediaFiles file = mediaFilesMapper.selectById(mediaId);
        if (
            file == null ||
            !String.valueOf(file.getCompanyId()).equals(String.valueOf(grant.get("companyId"))) ||
            !FILE_VIDEO.equals(file.getFileType())
        ) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "发布视频不存在或归属异常");
        if (!PROCESS_READY.equals(file.getStatus())) throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "视频尚未处理完成"
        );
        if (
            file.getBucket() == null || file.getFilePath() == null
        ) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "视频对象不存在");
        // 匿名试学地址最多60秒；正式学习仍不能超过登录有效期。
        int seconds = trial
            ? 60
            : (int) Math.min(
                  60,
                  Duration.between(Instant.now(), CurrentUser.jwt().getExpiresAt()).getSeconds()
              );
        if (seconds <= 0) throw new ResponseStatusException(
            HttpStatus.UNAUTHORIZED,
            "登录已过期，请重新登录"
        );
        try {
            minioClient.statObject(
                StatObjectArgs.builder().bucket(file.getBucket()).object(file.getFilePath()).build()
            );
            String url = minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(file.getBucket())
                    .object(file.getFilePath())
                    .expiry(seconds)
                    .build()
            );
            return new PlaybackUrlDto(courseId, lessonId, url, Instant.now().plusSeconds(seconds));
        } catch (ErrorResponseException missing) {
            if (
                Set.of("NoSuchKey", "NoSuchObject", "NoSuchBucket").contains(
                    missing.errorResponse().code()
                )
            ) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "视频对象不存在");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "视频存储暂不可用");
        } catch (Exception unavailable) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "暂时无法获取视频");
        }
    }

    /** 沿用封面路径解析、机构归属及就绪图片校验。 */
    @Override
    public Map<String, Object> coverInfo(Long company, String url) {
        String path = url;
        try {
            if (url.startsWith("http://") || url.startsWith("https://")) path = URI.create(
                url
            ).getPath();
        } catch (Exception error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "封面地址无效");
        }
        Matcher matcher = Pattern.compile(
            "/(?:api/media/|media/)?files/([a-fA-F0-9]{32})/content"
        ).matcher(path);
        MediaFiles file;
        if (matcher.find()) file = mediaFilesMapper.selectById(matcher.group(1));
        else file = mediaFilesMapper.selectOne(
            new LambdaQueryWrapper<MediaFiles>().eq(MediaFiles::getUrl, path).last("LIMIT 1")
        );
        if (file == null) {
            if (
                matcher.find(0) || path.startsWith("/mediafiles/")
            ) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "封面媒资不存在");
            // 外部图片不属于本服务对象，不纳入本地媒资删除流程。
            return Map.of("managed", false);
        }
        if (
            !Objects.equals(file.getCompanyId(), company) ||
            !FILE_IMAGE.equals(file.getFileType()) ||
            !PROCESS_READY.equals(file.getStatus())
        ) throw new ResponseStatusException(HttpStatus.CONFLICT, "封面不是本机构可用图片");
        return Map.of("managed", true, "id", file.getId());
    }

    /** 保留机构文件权限和公开封面核验，仅返回原短时效签名地址。 */
    @Override
    public String contentUrl(String id, Authentication authentication) throws Exception {
        MediaFiles file = mediaFilesMapper.selectById(id);
        if (
            file == null || PROCESS_DELETING.equals(file.getStatus())
        ) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在或正在删除");
        Number company =
            authentication instanceof JwtAuthenticationToken token
                ? token.getToken().getClaim("companyId")
                : null;
        boolean owner =
            authentication instanceof JwtAuthenticationToken token &&
            "institution".equals(token.getToken().getClaimAsString("role")) &&
            company != null &&
            file.getCompanyId() != null &&
            company.longValue() == file.getCompanyId().longValue();
        if (!owner) {
            if (!FILE_IMAGE.equals(file.getFileType())) throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "没有文件访问权限"
            );
            List<ServiceInstance> instances = discoveryClient.getInstances("content-api");
            if (instances.isEmpty()) throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "暂时无法核对封面发布状态"
            );
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(3000);
            factory.setReadTimeout(3000);
            Boolean published = RestClient.builder()
                .baseUrl(instances.getFirst().getUri().toString())
                .requestFactory(factory)
                .build()
                .get()
                .uri("/content/published-courses/covers/{id}", id)
                .retrieve()
                .body(Boolean.class);
            if (!Boolean.TRUE.equals(published)) throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "图片未作为已发布课程封面"
            );
        }
        String url = minioClient.getPresignedObjectUrl(
            GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(file.getBucket())
                .object(file.getFilePath())
                .expiry(300)
                .build()
        );
        return url;
    }

    /** 已就绪文件仍向 MinIO 确认对象存在，不返回失效资源。 */
    @Override
    public Map<String, Object> bindingInfo(Long companyId, String id) throws Exception {
        MediaFiles file = mediaFilesMapper.selectById(id);
        if (file == null || !companyId.equals(file.getCompanyId())) {
            return null;
        }
        // 已标记处理完成的记录还要确认对象仍在存储中，避免只凭数据库状态绑定失效文件。
        if (PROCESS_READY.equals(file.getStatus())) {
            if (file.getBucket() == null || file.getFilePath() == null) {
                return null;
            }
            try {
                minioClient.statObject(
                    StatObjectArgs.builder()
                        .bucket(file.getBucket())
                        .object(file.getFilePath())
                        .build()
                );
            } catch (ErrorResponseException missingObject) {
                return null;
            }
        }
        return Map.of(
            "id",
            file.getId(),
            "companyId",
            file.getCompanyId(),
            "filename",
            file.getFilename(),
            "status",
            file.getStatus() == null ? "" : file.getStatus()
        );
    }
}
