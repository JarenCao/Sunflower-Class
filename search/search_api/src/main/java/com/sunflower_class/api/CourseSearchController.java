package com.sunflower_class.api;

import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.service.search.service.CourseSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 学员搜索接口，控制器只接收参数并委托业务层。 */
@Tag(name = "课程搜索")
@RestController
public class CourseSearchController {

    @Autowired
    private CourseSearchService service;

    /** 默认每页十条，关键词、分类和总数均由 Elasticsearch 计算。 */
    @Operation(
        summary = "分页搜索已发布课程",
        description = "支持关键词和分类筛选，仅返回正式发布课程。"
    )
    @GetMapping("/courses")
    public PageResult<Map<String, Object>> list(
        @RequestParam(defaultValue = "1") long pageNo,
        @RequestParam(defaultValue = "10") long pageSize,
        @RequestParam(defaultValue = "") String q,
        @RequestParam(defaultValue = "") String category
    ) {
        return service.list(pageNo, pageSize, q, category);
    }

    /** 分类选项独立于当前页和关键词。 */
    @Operation(summary = "查询课程分类")
    @GetMapping("/categories")
    public List<String> categories() {
        return service.categories();
    }

    /** 读取正式索引详情，未发布与下架课程不公开。 */
    @Operation(
        summary = "查询公开课程详情",
        description = "读取正式发布快照，未发布或已下架课程不对外展示。"
    )
    @GetMapping("/courses/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        return service.get(id);
    }
}
