package com.sunflower_class.api;

import com.sunflower_class.base.security.CurrentUser;
import com.sunflower_class.service.content.service.CourseBaseInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 媒资删除前查询引用；只返回数量，不向机构暴露其他机构课程内容。 */
@Tag(name = "媒资删除前查询引用；只返回数量")
@RestController
public class MediaReferenceController {

    @Autowired
    private CourseBaseInfoService service;

    /** 使用Content自己的数据源，不让Media直接操作课程表。 */

    /** 草稿绑定、审核快照、已发布快照和封面均视为引用。 */
    @Operation(
        summary = "查询课程中的媒资引用",
        description = "仅返回引用数量，覆盖草稿、审核快照、正式快照与封面。"
    )
    @GetMapping("/media-references/{id}")
    public Map<String, Long> references(
        @PathVariable String id,
        @RequestParam(defaultValue = "") String url
    ) {
        CurrentUser.companyId();
        return service.mediaReferences(id, url);
    }
}
