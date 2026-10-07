package com.sunflower_class.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 课程审核请求，审核人和机构身份由服务端确定。 */
@Schema(description = "课程审核请求，审核人和机构身份由服务端确定。")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDecisionDto {

    /** 审核结论，空值表示尚未选择。 */
    @Schema(description = "审核结论，空值表示尚未选择。")
    private Boolean approved;

    /** 审核意见或驳回原因。 */
    @Schema(description = "审核意见或驳回原因。")
    private String reason;
}
