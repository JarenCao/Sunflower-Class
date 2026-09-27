package com.sunflower_class.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 网关启动入口，加载外部路由配置并将请求转发到各业务服务。
 */
@SpringBootApplication
public class GatewayApplication {

    /**
     * 启动 网关服务，将命令行参数交给 Spring Boot 加载配置。
     */
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
