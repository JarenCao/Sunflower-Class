package com.sunflower_class.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sunflower_class.base.exception.GlobalException;
import com.sunflower_class.base.model.RestResponse;
import com.sunflower_class.model.po.MqMessage;
import com.sunflower_class.service.content.mapper.MqMessageMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

/** 机构查看课程同步进度，并手动恢复达到自动重试上限的事件。 */
@RestController
public class CourseMessageController {

    private final MqMessageMapper messages;
    private final String companyId;

    /** 当前复用开发机构配置；正式机构身份由后续认证功能替换。 */
    public CourseMessageController(
        MqMessageMapper messages,
        @Value("${sunflower.company-id}") String companyId
    ) {
        this.messages = messages;
        this.companyId = companyId;
    }

    /** 返回本机构指定课程最近十条状态，不向浏览器暴露完整快照。 */
    @GetMapping("/publication-messages")
    public List<MqMessage> list(@RequestParam long courseId) {
        var items = messages.selectList(
            new LambdaQueryWrapper<MqMessage>()
                .eq(MqMessage::getMessageType, "course_publish")
                .isNotNull(MqMessage::getPayload)
                .eq(MqMessage::getBusinessKey1, Long.toString(courseId))
                .eq(MqMessage::getBusinessKey2, companyId)
                .orderByDesc(MqMessage::getId)
                .last("LIMIT 10")
        );
        items.forEach(item -> item.setPayload(null));
        return items;
    }

    /** 恢复未完成事件，已经完成或不属于本机构的事件不能重新安排。 */
    @PostMapping("/publication-messages/{id}/retry")
    public RestResponse retry(@PathVariable long id) {
        if (messages.retry(id, companyId) != 1) GlobalException.cast(
            "消息已完成、不存在或不属于本机构"
        );
        return RestResponse.success();
    }
}
