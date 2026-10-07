package com.sunflower_class;

import com.sunflower_class.base.exception.GlobalExceptionHandler;
import com.sunflower_class.base.security.IdentitySecurityConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/** API 模块启动认证微服务；扫描业务层并复用公共验签配置。 */
@MapperScan("com.sunflower_class.service.auth.mapper")
@SpringBootApplication(
    scanBasePackages = { "com.sunflower_class.api", "com.sunflower_class.service.auth" }
)
// 复用公共异常响应，将账号冲突等业务原因返回，保留原 HTTP 状态。
@Import({ IdentitySecurityConfig.class, GlobalExceptionHandler.class })
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
