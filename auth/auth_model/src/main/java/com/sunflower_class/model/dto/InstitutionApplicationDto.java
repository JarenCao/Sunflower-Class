package com.sunflower_class.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** 老师申请资料及独立老师账号；密码仅用于开户散列，不出现在查询响应。 */
@Schema(
    description = "老师申请资料及独立老师账号；密码仅用于开户散列，不出现在查询响应。",
    accessMode = Schema.AccessMode.WRITE_ONLY
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InstitutionApplicationDto {

    /** 老师教学空间名称，由六字段申请自动生成。 */
    @Schema(description = "老师教学空间名称，由六字段申请自动生成。")
    private String companyName;

    /** 申请老师姓名。 */
    @Schema(description = "申请老师姓名。")
    private String contact;

    /** 联系人手机号码。 */
    @Schema(description = "联系人手机号码。")
    private String mobile;

    /** 联系邮箱，六字段老师申请可为空。 */
    @Schema(description = "联系邮箱，六字段老师申请可为空。")
    private String email;

    /** 教学简介。 */
    @Schema(description = "教学简介。")
    private String intro;

    /** 登录用户名。 */
    @Schema(description = "登录用户名。")
    private String username;

    /** 显示名称。 */
    @Schema(description = "显示名称。")
    private String name;

    /** 请求密码，仅用于校验和散列，禁止写入日志。 */
    @Schema(
        description = "请求密码，仅用于校验和散列，禁止写入日志。",
        accessMode = Schema.AccessMode.WRITE_ONLY
    )
    @ToString.Exclude
    private String password;

    /** 确认密码，禁止写入日志。 */
    @Schema(description = "确认密码，禁止写入日志。", accessMode = Schema.AccessMode.WRITE_ONLY)
    @ToString.Exclude
    private String confirmPassword;
}
