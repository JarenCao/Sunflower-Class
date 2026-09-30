package com.sunflower_class.api;

import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.service.search.service.CourseSearchService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/** 学员搜索接口，控制器只接收参数并委托业务层。 */
@RestController
public class CourseSearchController {

    private final CourseSearchService service;

    /** 注入独立搜索服务，不从内容数据库直接查询课程。 */
    public CourseSearchController(CourseSearchService service) {
        this.service = service;
    }

    /** 默认每页十条，关键词、分类和总数均由 Elasticsearch 计算。 */
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
    @GetMapping("/categories")
    public List<String> categories() {
        return service.categories();
    }

    /** 读取正式索引详情，未发布与下架课程不公开。 */
    @GetMapping("/courses/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        return service.get(id);
    }
}
