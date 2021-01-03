package com.atguigu.gulimall.order;

import com.alibaba.cloud.seata.GlobalTransactionAutoConfiguration;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

@EnableRabbit
@EnableRedisHttpSession
@EnableFeignClients
@EnableDiscoveryClient
@SpringBootApplication(exclude = GlobalTransactionAutoConfiguration.class)//tip 启动时排除Seata配置
public class GulimallOrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(GulimallOrderApplication.class, args);
    }

}
