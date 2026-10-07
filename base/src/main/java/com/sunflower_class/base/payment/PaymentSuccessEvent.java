package com.sunflower_class.base.payment;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 同一订单使用稳定事件编号，paidAt为UTC，资格从可信支付时间起算，重放不能延长有效期。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSuccessEvent {

    /** 订单编号，避免前端长整数精度丢失。 */
    private String orderId;

    /** 原业务快照，字段结构与消息协议保持一致。 */
    private CourseOrderSnapshotDto snapshot;

    /** 可信支付时间，支付事件统一保存 UTC。 */
    private LocalDateTime paidAt;
}
