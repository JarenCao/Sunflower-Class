package com.sunflower_class.api;

import static com.sunflower_class.base.model.BusinessCodes.*;

import com.alibaba.nacos.common.http.param.MediaType;
import com.sunflower_class.base.exception.GlobalException;
import com.sunflower_class.base.model.PageParams;
import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.mapper.MediaFilesMapper;
import com.sunflower_class.model.dto.QueryMediaParamsDto;
import com.sunflower_class.model.dto.UploadFileParamsDto;
import com.sunflower_class.model.dto.UploadFileResultDto;
import com.sunflower_class.model.po.MediaFiles;
import com.sunflower_class.service.MediaFileService;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 媒资查询、普通上传与文件访问接口，通过短期签名地址访问对象存储。
 */
@Tag(name = "媒资文件管理", description = "媒资文件管理接口")
@CrossOrigin(origins = "*")
@RestController
public class MediaFilesController {

    @Value("${sunflower.company-id}")
    private Long companyId;

    @Autowired
    private MediaFileService mediaFileService;

    @Autowired
    private MediaFilesMapper mediaFilesMapper;

    @Autowired
    private MinioClient minioClient;

    /** 校验开发机构归属后签发短期访问地址，前端不持有对象存储凭据。 */
    @GetMapping("/files/{id}/content")
    public ResponseEntity<Void> content(@PathVariable String id) throws Exception {
        MediaFiles file = mediaFilesMapper.selectById(id);
        if (file == null || !companyId.equals(file.getCompanyId())) GlobalException.cast(
            "文件不存在或不属于当前机构"
        );
        String url = minioClient.getPresignedObjectUrl(
            GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(file.getBucket())
                .object(file.getFilePath())
                .expiry(300)
                .build()
        );
        return ResponseEntity.status(302).location(URI.create(url)).build();
    }

    /** 核对机构、处理状态及可用对象，向内容服务返回不含存储路径的元数据。 */
    @GetMapping("/files/{id}/binding-info")
    public ResponseEntity<Map<String, Object>> bindingInfo(@PathVariable String id)
        throws Exception {
        MediaFiles file = mediaFilesMapper.selectById(id);
        if (file == null || !companyId.equals(file.getCompanyId())) {
            return ResponseEntity.notFound().build();
        }
        // 已标记处理完成的记录还要确认对象仍在存储中，避免只凭数据库状态绑定失效文件。
        if (PROCESS_READY.equals(file.getStatus())) {
            if (file.getBucket() == null || file.getFilePath() == null) {
                return ResponseEntity.notFound().build();
            }
            try {
                minioClient.statObject(
                    StatObjectArgs.builder()
                        .bucket(file.getBucket())
                        .object(file.getFilePath())
                        .build()
                );
            } catch (ErrorResponseException missingObject) {
                return ResponseEntity.notFound().build();
            }
        }
        return ResponseEntity.ok(
            Map.of(
                "id",
                file.getId(),
                "companyId",
                file.getCompanyId(),
                "filename",
                file.getFilename(),
                "status",
                file.getStatus() == null ? "" : file.getStatus()
            )
        );
    }

    /**
     * 接收分页参数和可选媒资筛选条件，返回配置机构下的文件列表。
     */
    @Operation(
        summary = "媒资列表查询",
        description = "分页查询媒资文件列表，支持按文件名、文件类型等条件筛选"
    )
    @PostMapping("/files")
    public PageResult<MediaFiles> list(
        PageParams pageParams,
        @RequestBody(required = false) QueryMediaParamsDto queryMediaParamsDto
    ) {
        return mediaFileService.queryMediaFiels(companyId, pageParams, queryMediaParamsDto);
    }

    /**
     * 接收名为 filedata 的 multipart 文件，构造上传参数并返回保存后的媒资信息。
     */
    @Operation(summary = "上传媒资文件", description = "上传课程媒资文件到MinIO")
    @PostMapping(value = "/upload/coursefile", consumes = MediaType.MULTIPART_FORM_DATA)
    public UploadFileResultDto upload(@RequestPart("filedata") MultipartFile filedata) {
        UploadFileParamsDto params = new UploadFileParamsDto();

        params.setFilename(filedata.getOriginalFilename());

        params.setFileSize(filedata.getSize());

        params.setFileType(
            filedata.getContentType() != null && filedata.getContentType().startsWith("video/")
                ? FILE_VIDEO
                : FILE_IMAGE
        );

        return mediaFileService.uploadFiles(companyId, filedata, params);
    }
}
