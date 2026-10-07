package com.sunflower_class.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** 登录请求只接收用户名和密码；不接受客户端指定身份或机构。 */
@Schema(
    description = "登录请求只接收用户名和密码；不接受客户端指定身份或机构。",
    accessMode = Schema.AccessMode.WRITE_ONLY
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequestDto {

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

    /** 登录请求的日志输出始终隐藏密码。 */
    @Override
    public String toString() {
        return "LoginRequestDto[credentials=REDACTED]";
    }
}
