package com.sunflower_class.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sunflower_class.base.model.RestResponse;
import com.sunflower_class.model.dto.UploadFileParamsDto;
import com.sunflower_class.service.MediaFileService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@CrossOrigin(origins = "*")
@RestController
@Tag(name = "大文件上传", description = "大文件分片上传接口")
public class BigFilesController {

    @Autowired
    private MediaFileService mediaFileService;

    @Operation(
        summary = "检查文件是否存在",
        description = "根据文件MD5检查文件是否已上传，用于断点续传"
    )
    @PostMapping("/upload/checkfile")
    public RestResponse<Boolean> checkfile(
            @Parameter(description = "文件MD5值", required = true, example = "e10adc3949ba59abbe56e057f20f883e")
            @RequestParam("fileMd5") String fileMd5) throws Exception {
        return mediaFileService.checkFile(fileMd5);
    }

    @Operation(
        summary = "检查分片是否存在",
        description = "检查指定文件的分片是否已上传"
    )
    @PostMapping("/upload/checkchunk")
    public RestResponse<Boolean> checkchunk(
            @Parameter(description = "文件MD5值", required = true, example = "e10adc3949ba59abbe56e057f20f883e")
            @RequestParam("fileMd5") String fileMd5,
            @Parameter(description = "分片索引（从0开始）", required = true, example = "0")
            @RequestParam("chunk") int chunk) throws Exception {
        return mediaFileService.checkChunk(fileMd5, chunk);
    }

    @Operation(
        summary = "上传分片",
        description = "上传文件的一个分片"
    )
    @PostMapping("/upload/uploadchunk")
    public RestResponse uploadchunk(
            @Parameter(description = "分片文件", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "文件MD5值", required = true, example = "e10adc3949ba59abbe56e057f20f883e")
            @RequestParam("fileMd5") String fileMd5,
            @Parameter(description = "分片索引（从0开始）", required = true, example = "0")
            @RequestParam("chunk") int chunk) throws Exception {
        return mediaFileService.uploadChunk(fileMd5, chunk, file);
    }

    @Operation(
        summary = "合并分片",
        description = "合并所有已上传的分片，完成文件上传"
    )
    @PostMapping("/upload/mergechunks")
    public RestResponse mergechunks(
            @Parameter(description = "文件MD5值", required = true, example = "e10adc3949ba59abbe56e057f20f883e")
            @RequestParam("fileMd5") String fileMd5,
            @Parameter(description = "文件名", required = true, example = "课程视频.mp4")
            @RequestParam("fileName") String fileName,
            @Parameter(description = "分片总数", required = true, example = "5")
            @RequestParam("chunkTotal") int chunkTotal) throws Exception {

        Long companyId = 1111L;
        UploadFileParamsDto uploadFileParamsDto = new UploadFileParamsDto();
        uploadFileParamsDto.setFileType("20102");
        uploadFileParamsDto.setTags("课程视频");
        uploadFileParamsDto.setRemark("");
        uploadFileParamsDto.setFilename(fileName);
        return mediaFileService.mergechunks(companyId, fileMd5, chunkTotal, uploadFileParamsDto);
    }
}