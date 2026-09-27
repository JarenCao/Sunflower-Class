package com.sunflower_class;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 内容模块集成测试的 Spring Boot 启动配置。
 */
@SpringBootApplication
public class TestApplication {

    /**
     * 启动 内容模块测试应用，将命令行参数交给 Spring Boot 加载配置。
     */
    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
