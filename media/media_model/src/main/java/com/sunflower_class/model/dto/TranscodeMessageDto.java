package com.sunflower_class.model.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class TranscodeMessageDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private String fileMd5;
    private String filename;
    private String bucket;
    private String filePath;
}
