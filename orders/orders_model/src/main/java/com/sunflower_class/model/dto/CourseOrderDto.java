package com.sunflower_class.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 大整数订单号作为字符串返回，避免浏览器精度损失。 */
@Schema(description = "大整数订单号作为字符串返回，避免浏览器精度损失。")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseOrderDto {

    /** 订单编号，以字符串返回，避免浏览器整数精度损失。 */
    @Schema(description = "订单编号，以字符串返回，避免浏览器整数精度损失。")
    private String id;

    /** 课程编号。 */
    @Schema(description = "课程编号。")
    private long courseId;

    /** 课程名称快照。 */
    @Schema(description = "课程名称快照。")
    private String courseName;

    /** 价格快照，由服务端读取，不能采用客户端价格。 */
    @Schema(description = "价格快照，由服务端读取，不能采用客户端价格。")
    private BigDecimal price;

    /** 订单状态：60201 待支付、60202 已支付、60203 已关闭、60204 已退款、60205 已完成。 */
    @Schema(
        description = "订单状态：60201 待支付、60202 已支付、60203 已关闭、60204 已退款、60205 已完成。"
    )
    private String status;

    /** 创建时间。 */
    @Schema(description = "创建时间。")
    private LocalDateTime createdAt;

    /** 有效截止时间。 */
    @Schema(description = "有效截止时间。")
    private LocalDateTime expiresAt;

    /** 学习资格是否已经开通。 */
    @Schema(description = "学习资格是否已经开通。")
    private boolean learningActivated;
}
