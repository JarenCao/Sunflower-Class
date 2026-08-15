package com.sunflower_class.model.po;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@TableName("mq_message_history")
@Schema(description = "MQ消息历史记录")
public class MqMessageHistory implements Serializable {

    @Schema(description = "消息ID", example = "msg_1234567890")
    private String id;

    @Schema(description = "消息类型代码", example = "course_publish")
    private String messageType;

    @Schema(description = "关联业务Key1", example = "course_001")
    private String businessKey1;

    @Schema(description = "关联业务Key2", example = "user_001")
    private String businessKey2;

    @Schema(description = "关联业务Key3", example = "order_001")
    private String businessKey3;

    @Schema(description = "MQ主机地址", example = "localhost")
    private String mqHost;

    @Schema(description = "MQ端口", example = "5672")
    private Integer mqPort;

    @Schema(description = "MQ虚拟主机", example = "/")
    private String mqVirtualhost;

    @Schema(description = "队列名称", example = "course.queue")
    private String mqQueue;

    @Schema(description = "通知次数", example = "3")
    private Integer informNum;

    @Schema(description = "处理状态（0-初始，1-成功，2-失败）", example = "1", allowableValues = { "0", "1", "2" })
    private Integer state;

    @Schema(description = "回复失败时间")
    private LocalDateTime returnfailureDate;

    @Schema(description = "回复成功时间")
    private LocalDateTime returnsuccessDate;

    @Schema(description = "回复失败内容", example = "连接超时")
    private String returnfailureMsg;

    @Schema(description = "最近通知时间")
    private LocalDateTime informDate;
}