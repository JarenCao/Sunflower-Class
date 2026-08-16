package com.sunflower_class.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sunflower_class.base.model.PageParams;
import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.model.dto.QueryMediaParamsDto;
import com.sunflower_class.model.po.MediaFiles;
import com.sunflower_class.service.MediaFileService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "媒资文件管理", description = "媒资文件管理接口")
@RestController
public class MediaFilesController {

    @Autowired
    private MediaFileService mediaFileService;

    @Operation(summary = "媒资列表查询", description = "分页查询媒资文件列表，支持按文件名、文件类型等条件筛选")
    @PostMapping("/files")
    public PageResult<MediaFiles> list(
            @RequestParam PageParams pageParams,
            @RequestBody(required = false) QueryMediaParamsDto queryMediaParamsDto) {

        Long companyId = 1000L;
        return mediaFileService.queryMediaFiels(companyId, pageParams, queryMediaParamsDto);
    }
}