package com.sunflower_class.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** 学员注册和老师开户共用表单，不允许请求指定角色或机构。 */
@Schema(description = "学员注册和老师开户共用表单，不允许请求指定角色或机构。")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequestDto {

    /** 登录用户名。 */
    @Schema(description = "登录用户名。")
    private String username;

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

    /** 显示名称。 */
    @Schema(description = "显示名称。")
    private String name;
}
