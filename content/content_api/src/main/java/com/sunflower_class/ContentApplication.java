package com.sunflower_class;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 课程内容服务启动入口，加载内容模块的接口和业务组件。
 */
@SpringBootApplication
@EnableScheduling
public class ContentApplication {

    /**
     * 启动 课程内容服务，将命令行参数交给 Spring Boot 加载配置。
     */
    public static void main(String[] args) {
        SpringApplication.run(ContentApplication.class, args);
    }
}
