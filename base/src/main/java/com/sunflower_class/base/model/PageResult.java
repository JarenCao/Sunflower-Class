package com.sunflower_class.base.model;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

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

    public PageResult(List<T> items, Long count, Long page, Long pageSize) {
        this.items = items;
        this.count = count;
        this.page = page;
        this.pageSize = pageSize;
    }

}
