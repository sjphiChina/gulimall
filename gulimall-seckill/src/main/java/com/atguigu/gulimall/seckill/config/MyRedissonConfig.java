package com.atguigu.gulimall.seckill.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class MyRedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(@Value("${spring.redis.host}") String host) throws IOException {

        Config config = new Config();
        config.useSingleServer().setAddress("redis://"+host+":6379");

        RedissonClient redissonClient = Redisson.create(config);
        return redissonClient;
    }
}
