package com.atguigu.gulimall.search.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
public class MyRedisSessionConfig {

    @Bean
    public LettuceConnectionFactory connectionFactory() {
        return new LettuceConnectionFactory("192.168.99.10", 6379);
    }

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("GULISESSION");
        serializer.setCookiePath("/");
        serializer.setDomainName("gulimall.com");
        return serializer;
    }

    @Bean
    public RedisSerializer<Object> springSessionDefaultRedisSerializer (){
        return new GenericJackson2JsonRedisSerializer();
    }
}
