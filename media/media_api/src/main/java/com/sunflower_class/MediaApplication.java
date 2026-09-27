package com.sunflower_class;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 媒资服务启动入口，负责文件管理与异步视频处理相关组件。
 */
@EnableScheduling
@SpringBootApplication
public class MediaApplication {

    /**
     * 启动 媒资服务，将命令行参数交给 Spring Boot 加载配置。
     */
    public static void main(String[] args) {
        SpringApplication.run(MediaApplication.class, args);
    }
}
