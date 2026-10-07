package com.sunflower_class.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 支付宝查询及验签后的交易结果，不接受前端伪造的支付结论。 */
@Schema(description = "支付宝查询及验签后的交易结果，不接受前端伪造的支付结论。")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlipayTradeDto {

    /** 本地支付单号。 */
    @Schema(description = "本地支付单号。")
    private String payNo;

    /** 支付宝平台交易流水号。 */
    @Schema(description = "支付宝平台交易流水号。")
    private String tradeNo;

    /** 平台确认的交易金额。 */
    @Schema(description = "平台确认的交易金额。")
    private BigDecimal amount;

    /** 支付宝返回的北京时间；订单服务转换为 UTC 后写入支付事件。 */
    @Schema(description = "支付宝返回的北京时间；订单服务转换为 UTC 后写入支付事件。")
    private LocalDateTime paidAt;

    /** 支付宝交易状态，如 WAIT_BUYER_PAY、TRADE_SUCCESS、TRADE_FINISHED、TRADE_CLOSED。 */
    @Schema(
        description = "支付宝交易状态，如 WAIT_BUYER_PAY、TRADE_SUCCESS、TRADE_FINISHED、TRADE_CLOSED。"
    )
    private String status;
}
