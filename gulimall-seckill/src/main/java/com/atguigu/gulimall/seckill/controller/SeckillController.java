package com.atguigu.gulimall.seckill.controller;

import com.atguigu.common.utils.R;
import com.atguigu.gulimall.seckill.service.SeckillService;
import com.atguigu.gulimall.seckill.to.SecKillSkuRedisTo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
public class SeckillController {
    @Autowired
    SeckillService seckillService;

    //找到当前所有可以参与秒杀服务的商品信息
    @GetMapping("/currentSeckillSkus")
    public R getCurrentSeckillSkus() {
        List<SecKillSkuRedisTo> vos = seckillService.getCurrentSeckillSkus();
        if (vos != null)
            log.debug("当前可参与秒杀的商品: {}", vos.stream().map(item -> {
                return item.getSkuId();
            }).collect(Collectors.toList()));
        else
            log.warn("没有可供秒杀的商品");
        return R.ok().setData(vos);
    }

    @GetMapping("/sku/seckill/{skuId}")
    public R getSkuSeckillInfo(@PathVariable("skuId") Long skuId) {
        SecKillSkuRedisTo vo = seckillService.getSkuSeckillInfo(skuId);
        return R.ok().setData(vo);
    }
}
