package com.atguigu.gulimall.product.config;

import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@EnableConfigurationProperties(CacheProperties.class)
@Configuration
@EnableCaching
public class MySpringCacheConfig {
    @Bean
    RedisCacheConfiguration redisCacheConfiguration(CacheProperties cacheProperties) {
        RedisCacheConfiguration redisCacheConfiguration = RedisCacheConfiguration.defaultCacheConfig();
        redisCacheConfiguration = redisCacheConfiguration.serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()));
        redisCacheConfiguration = redisCacheConfiguration.serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));

        CacheProperties.Redis redisProperties = cacheProperties.getRedis();
        if (redisProperties.getKeyPrefix() != null)
            redisCacheConfiguration = redisCacheConfiguration.prefixKeysWith(redisProperties.getKeyPrefix());
        if (redisProperties.getTimeToLive() != null)
            redisCacheConfiguration = redisCacheConfiguration.entryTtl(redisProperties.getTimeToLive());
        if (!redisProperties.isCacheNullValues())
            redisCacheConfiguration = redisCacheConfiguration.disableCachingNullValues();
        if (!redisProperties.isUseKeyPrefix())
            redisCacheConfiguration = redisCacheConfiguration.disableKeyPrefix();

        //将配置文件中的所有配置都生效
        return redisCacheConfiguration;
    }
}
