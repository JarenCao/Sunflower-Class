package com.sunflower_class;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 学习目录服务启动入口，目录层级与内容及媒资服务保持一致。 */
@MapperScan("com.sunflower_class.service.learning.mapper")
@SpringBootApplication
public class LearningApplication {

    /** 启动独立服务并加载 Nacos 外部配置。 */
    public static void main(String[] args) {
        SpringApplication.run(LearningApplication.class, args);
    }
}
