package com.sunflower_class.base.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 分页请求参数，封装从 1 开始的页码和每页记录数。
 */
@Data
@Schema(description = "分页参数")
public class PageParams {

    @Schema(description = "当前页码", example = "1", defaultValue = "1")
    private Long pageNo = 1L;

    @Schema(description = "每页条数", example = "30", defaultValue = "30")
    private Long pageSize = 30L;

    /**
     * 创建分页参数；无参构造保留字段默认值，有参构造使用指定页码和每页数量。
     */
    public PageParams() {}

    /**
     * 创建分页参数；无参构造保留字段默认值，有参构造使用指定页码和每页数量。
     */
    public PageParams(Long pageNo, Long pageSize) {
        this.pageNo = pageNo;
        this.pageSize = pageSize;
    }
}
