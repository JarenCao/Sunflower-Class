package com.sunflower_class.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.alibaba.nacos.common.http.param.MediaType;
import com.sunflower_class.base.model.PageParams;
import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.model.dto.QueryMediaParamsDto;
import com.sunflower_class.model.dto.UploadFileParamsDto;
import com.sunflower_class.model.dto.UploadFileResultDto;
import com.sunflower_class.model.po.MediaFiles;
import com.sunflower_class.service.MediaFileService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "媒资文件管理", description = "媒资文件管理接口")
@CrossOrigin(origins = "*")
@RestController
public class MediaFilesController {

    @Autowired
    private MediaFileService mediaFileService;

    @Operation(summary = "媒资列表查询", description = "分页查询媒资文件列表，支持按文件名、文件类型等条件筛选")
    @PostMapping("/files")
    public PageResult<MediaFiles> list(
            PageParams pageParams,
            @RequestBody(required = false) QueryMediaParamsDto queryMediaParamsDto) {

        Long companyId = 1000L;

        return mediaFileService.queryMediaFiels(companyId, pageParams, queryMediaParamsDto);
    }

    @Operation(summary = "上传媒资文件", description = "上传课程媒资文件到MinIO")
    @PostMapping(value = "/upload/coursefile", consumes = MediaType.MULTIPART_FORM_DATA)
    public UploadFileResultDto upload(@RequestPart("filedata") MultipartFile filedata) {

        Long companyId = 1002233L;

        UploadFileParamsDto params = new UploadFileParamsDto();

        params.setFilename(filedata.getOriginalFilename());

        params.setFileSize(filedata.getSize());

        params.setFileType("20101");

        return mediaFileService.uploadFiles(companyId, filedata, params);
    }
}