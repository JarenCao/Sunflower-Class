package com.sunflower_class.base.model;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 分页查询结果，携带当前页记录及分页器需要的总量信息。
 */
@Data
public class PageResult<T> implements Serializable {

    // 数据列表
    private List<T> items;
    // 总记录数
    private Long count;
    // 当前页面
    private Long page;
    // 每条记录数
    private Long pageSize;

    /**
     * 封装当前页记录、总记录数、页码和每页数量，供前端分页器使用。
     */
    public PageResult(List<T> items, Long count, Long page, Long pageSize) {
        this.items = items;
        this.count = count;
        this.page = page;
        this.pageSize = pageSize;
    }
}
