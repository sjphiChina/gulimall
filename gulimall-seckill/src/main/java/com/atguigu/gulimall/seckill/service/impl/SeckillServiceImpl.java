package com.atguigu.gulimall.seckill.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.seckill.feign.CouponFeignService;
import com.atguigu.gulimall.seckill.feign.ProductFeignService;
import com.atguigu.gulimall.seckill.service.SeckillService;
import com.atguigu.gulimall.seckill.to.SecKillSkuRedisTo;
import com.atguigu.gulimall.seckill.vo.SeckillSessionsWithSkus;
import com.atguigu.gulimall.seckill.vo.SeckillSkuVo;
import com.atguigu.gulimall.seckill.vo.SkuInfoVo;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.xml.crypto.Data;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SeckillServiceImpl implements SeckillService {

    @Autowired
    CouponFeignService couponFeignService;

    @Autowired
    ProductFeignService productFeignService;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    RedissonClient redissonClient;

    private final static String SESSIONS_CACHE_PREFIX = "seckill:sessions:";
    private final static String SKUKILL_CACHE_PREFIX = "seckill:skus:";
    private final static String SKU_STOCK_SEMAPHORE = "seckill:stock";//+商品随机码

    @Override
    public void uploadSeckillSkuLatest3Days() {
        // 扫描最近3天需要参与秒杀的活动
        R session = couponFeignService.getLatest3DaySession();
        if (session.getCode() == 0) {
            //上架商品
            List<SeckillSessionsWithSkus> sessionData = session.getData(new TypeReference<List<SeckillSessionsWithSkus>>() {});
            if (sessionData != null && sessionData.size() > 0) {
                //缓存到redis
                //1. 缓存活动信息
                saveSessionInfos(sessionData);
                //2. 缓存活动的关联商品信息
                saveSessionSkuInfos(sessionData);
            }
        }
    }

    @Override
    public List<SecKillSkuRedisTo> getCurrentSeckillSkus() {
        // 确定当前时间属于哪个秒杀场次
        // 1970 -
        long time = new Date().getTime();
        Set<String> keys = redisTemplate.keys(SESSIONS_CACHE_PREFIX + "*");
        for (String key: keys) {
            String replace = key.replace(SESSIONS_CACHE_PREFIX, "");
            String[] s = replace.split("_");
            Long start = Long.parseLong(s[0]);
            Long end = Long.parseLong(s[1]);
            if (time >= start && time <= end) {
                // 获取这个秒杀场次需要的所有商品信息
                List<String> range = redisTemplate.opsForList().range(key, -100, 100);
                BoundHashOperations<String, String, String> ops = redisTemplate.boundHashOps(SKUKILL_CACHE_PREFIX);
                List<String> list = ops.multiGet(range);
                if (list != null && list.size() > 0) {
                    List<SecKillSkuRedisTo> collect = list.stream().map(item->{
                        SecKillSkuRedisTo redisTo = JSON.parseObject((String) item, SecKillSkuRedisTo.class);
                        //redisTo.setRandomCode(null);
                        return redisTo;
                    }).collect(Collectors.toList());
                    return collect;
                }
                break;
            }
        }
        return null;
    }

    @Override
    public SecKillSkuRedisTo getSkuSeckillInfo(Long skuId) {
        // 1. 找到所有参与秒杀的商品的key
        BoundHashOperations<String, String, String> ops = redisTemplate.boundHashOps(SKUKILL_CACHE_PREFIX);
        Set<String> keys = ops.keys();
        if (keys != null && keys.size() > 0) {
            String regx = "\\d_" + skuId;
            for (String key : keys) {
                //6_4
                // 匹配skuid
                if (Pattern.matches(regx, key)) {
                    String json = ops.get(key);
                    SecKillSkuRedisTo to = JSON.parseObject(json, SecKillSkuRedisTo.class);
                    // case 查看是否需要携带验证码
                    long currentTime = new Date().getTime();
                    if (to.getEndTime() < currentTime || to.getStartTime() > currentTime)
                        to.setRandomCode(null);
                    return to;
                }
            }
        }
        return null;
    }

    private void saveSessionInfos(List<SeckillSessionsWithSkus> sessions) {
        sessions.stream().forEach(session -> {
            Long startTime = session.getStartTime().getTime();
            Long endTime = session.getEndTime().getTime();
            String key = SESSIONS_CACHE_PREFIX + startTime + "_" + endTime;
            //重复的数据就不能再存了
            if (!redisTemplate.hasKey(key)) {
                //场次id+skuiId作为key
                List<String> skuIds = session.getRelationSkus().stream()
                        .map(item -> item.getPromotionSessionId() + "_" + item.getSkuId().toString())
                        .collect(Collectors.toList());
                redisTemplate.opsForList().leftPushAll(key, skuIds);
            }
        });
    }

    private void saveSessionSkuInfos(List<SeckillSessionsWithSkus> sessions) {

        sessions.stream().forEach(session -> {
            //准备hash操作
            BoundHashOperations<String, Object, Object> ops = redisTemplate.boundHashOps(SKUKILL_CACHE_PREFIX);
            session.getRelationSkus().stream().forEach(skuVo -> {
                String key = skuVo.getPromotionSessionId() + "_" + skuVo.getSkuId();
                if (ops.hasKey(key))
                    return;
                //缓存商品
                SecKillSkuRedisTo redisTo = new SecKillSkuRedisTo();
                // 1. sku的基本数据
                R skuInfo = productFeignService.getSkuInfo(skuVo.getSkuId());
                if (skuInfo.getCode() == 0) {
                    SkuInfoVo skuInfoVo = skuInfo.getData("skuInfo", new TypeReference<SkuInfoVo>() {});
                    redisTo.setSkuInfoVo(skuInfoVo);
                }
                // 2. sku的秒杀信息
                BeanUtils.copyProperties(skuVo, redisTo);
                // 3. 设置当前商品的秒杀时间信息
                redisTo.setStartTime(session.getEndTime().getTime());
                redisTo.setEndTime(session.getEndTime().getTime());
                // 4. know 秒杀随机码 与 分布式信号量 video315 一种保护机制 在秒杀开始的那一刻暴露出来，防止被人提前使用url获取商品
                String token = UUID.randomUUID().toString().replace("-", "");
                redisTo.setRandomCode(token);
                // 存入redis
                String skuAllInfo = JSON.toJSONString(redisTo);
                ops.put(key, skuAllInfo);

                // 如果当前这个场次的商品库存信息已经上架就不需要再上架了
                // 5. 使用库存作为分布式的信号量来限流
                RSemaphore semaphore = redissonClient.getSemaphore(SKU_STOCK_SEMAPHORE + token);
                // 此商品的数量可以作为信号量
                semaphore.trySetPermits(skuVo.getSeckillCount().intValue());
            });
        });
    }
}
