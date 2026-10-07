package com.sunflower_class;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 订单微服务只连接 orders 库，复用共享认证与消息基础设施。 */
@MapperScan("com.sunflower_class.service.orders.mapper")
@SpringBootApplication
@EnableScheduling
public class OrdersApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrdersApplication.class, args);
    }
}
