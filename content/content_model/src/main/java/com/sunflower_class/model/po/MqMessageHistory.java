package com.sunflower_class.model.po;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * MQ消息历史记录
 */
@Data
@TableName("mq_message_history")
@Schema(description = "MQ消息历史记录")
public class MqMessageHistory {

    @Schema(description = "主键ID", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "消息类型")
    private String messageType;

    @Schema(description = "关联业务Key1（如课程ID）")
    private String businessKey1;

    @Schema(description = "关联业务Key2（如公司ID）")
    private String businessKey2;

    @Schema(description = "关联业务Key3（扩展字段，如消息内容JSON）")
    private String businessKey3;

    @Schema(description = "执行次数（重试计数）")
    private Integer executeNum;

    @Schema(description = "处理状态（0-初始，1-成功，2-失败）", example = "0", allowableValues = { "0", "1", "2" })
    private String state;

    @Schema(description = "回复失败时间")
    private LocalDateTime returnfailureDate;

    @Schema(description = "回复成功时间")
    private LocalDateTime returnsuccessDate;

    @Schema(description = "回复失败内容")
    private String returnfailureMsg;

    @Schema(description = "最近通知时间")
    private LocalDateTime executeDate;

    @Schema(description = "阶段1处理状态（0-初始，1-成功,2-失败）- MQ消息发送", example = "0", allowableValues = { "0", "1", "2" })
    private String stageState1;

    @Schema(description = "阶段2处理状态（0-初始，1-成功,2-失败）- 课程缓存Redis", example = "0", allowableValues = { "0", "1", "2" })
    private String stageState2;

    @Schema(description = "阶段3处理状态（0-初始，1-成功,2-失败）- 搜索服务同步", example = "0", allowableValues = { "0", "1", "2" })
    private String stageState3;

    @Schema(description = "阶段4处理状态（0-初始，1-成功,2-失败）- 订单服务同步", example = "0", allowableValues = { "0", "1", "2" })
    private String stageState4;
}