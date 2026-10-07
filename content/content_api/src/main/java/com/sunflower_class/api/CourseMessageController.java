package com.sunflower_class.api;

import com.sunflower_class.base.model.RestResponse;
import com.sunflower_class.base.security.CurrentUser;
import com.sunflower_class.model.po.MqMessage;
import com.sunflower_class.service.content.service.CoursePublishService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 机构查看课程同步进度，并手动恢复达到自动重试上限的事件。 */
@Tag(name = "机构查看课程同步进度")
@RestController
@RequestMapping("/publication-messages")
public class CourseMessageController {

    @Autowired
    private CoursePublishService coursePublishService;

    /** 机构身份在每次请求中读取，不能缓存为单例字段。 */

    /** 返回本机构指定课程最近十条状态，不向浏览器暴露完整快照。 */
    @Operation(
        summary = "查询课程同步消息状态",
        description = "仅返回本人教学空间课程最近十条消息，不返回完整快照。"
    )
    @GetMapping
    public List<MqMessage> list(@RequestParam long courseId) {
        return coursePublishService.publicationMessages(CurrentUser.companyId(), courseId);
    }

    /** 恢复未完成事件，已经完成或不属于本机构的事件不能重新安排。 */
    @Operation(
        summary = "重试未完成课程同步消息",
        description = "仅允许本人教学空间未完成事件重试。"
    )
    @PostMapping("/{id}/retry")
    public RestResponse retry(@PathVariable long id) {
        coursePublishService.retryPublicationMessage(CurrentUser.companyId(), id);
        return RestResponse.success();
    }
}
