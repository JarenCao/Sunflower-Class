package com.sunflower_class.base.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "分页参数")
public class PageParams {

    @Schema(description = "当前页码", example = "1", defaultValue = "1")
    private Long pageNo = 1L;

    @Schema(description = "每页条数", example = "30", defaultValue = "30")
    private Long pageSize = 30L;

    public PageParams() {
    }

    public PageParams(Long pageNo, Long pageSize) {
        this.pageNo = pageNo;
        this.pageSize = pageSize;
    }
}