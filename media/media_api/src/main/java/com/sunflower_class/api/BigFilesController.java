package com.sunflower_class.api;

import static com.sunflower_class.base.model.BusinessCodes.FILE_VIDEO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sunflower_class.base.model.RestResponse;
import com.sunflower_class.model.dto.UploadFileParamsDto;
import com.sunflower_class.service.MediaFileService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * 视频分片上传接口，串联完整文件检查、分片检查、分片上传与合并。
 */
@Slf4j
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/upload")
@Tag(name = "大文件上传", description = "大文件分片上传接口")
public class BigFilesController {

    @Value("${sunflower.company-id}")
    private Long companyId;

    @Autowired
    private MediaFileService mediaFileService;

    /**
     * 按文件 MD5 查询媒资记录及存储对象是否存在，返回结果供前端判断是否跳过上传。
     */
    @Operation(summary = "检查文件是否存在", description = "根据文件MD5检查文件是否已上传，用于断点续传")
    @PostMapping("/checkfile")
    public RestResponse<Boolean> checkfile(@RequestParam("fileMd5") String fileMd5)
            throws Exception {
        return mediaFileService.checkFile(fileMd5);
    }

    /**
     * 按整文件 MD5 与从 0 开始的分片编号检查分片是否已上传。
     */
    @Operation(summary = "检查分片是否存在", description = "检查指定文件的分片是否已上传")
    @PostMapping("/checkchunk")
    public RestResponse<Boolean> checkchunk(
            @RequestParam("fileMd5") String fileMd5,
            @RequestParam("chunk") int chunk) throws Exception {
        return mediaFileService.checkChunk(fileMd5, chunk);
    }

    /**
     * 接收单个分片及所属文件 MD5，将分片交给媒资服务保存。
     */
    @Operation(summary = "上传分片", description = "上传文件的一个分片")
    @PostMapping("/uploadchunk")
    public RestResponse uploadchunk(
            @RequestParam("file") MultipartFile file,
            @RequestParam("fileMd5") String fileMd5,
            @RequestParam("chunk") int chunk) throws Exception {
        return mediaFileService.uploadChunk(fileMd5, chunk, file);
    }

    /**
     * 检查并合并视频分片，校验整文件 MD5 后保存媒资元数据和转码任务，再清理分片。
     */
    @Operation(summary = "合并分片", description = "合并所有已上传的分片，完成文件上传")
    @PostMapping("/mergechunks")
    public RestResponse mergechunks(
            @RequestParam("fileMd5") String fileMd5,
            @RequestParam("fileName") String fileName,
            @RequestParam("chunkTotal") int chunkTotal) throws Exception {
        UploadFileParamsDto uploadFileParamsDto = new UploadFileParamsDto();

        uploadFileParamsDto.setFileType(FILE_VIDEO);

        uploadFileParamsDto.setTags("课程视频");

        uploadFileParamsDto.setRemark("");

        uploadFileParamsDto.setFilename(fileName);

        return mediaFileService.mergechunks(companyId, fileMd5, chunkTotal, uploadFileParamsDto);
    }
}