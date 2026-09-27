package com.sunflower_class.model.dto;

import com.sunflower_class.model.po.MediaFiles;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 上传返回模型，向调用方提供已保存的媒资信息。
 */
@Schema(description = "上传文件返回结果")
public class UploadFileResultDto extends MediaFiles {}
