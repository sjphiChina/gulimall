package com.atguigu.gulimall.seckill.controller;

import com.atguigu.common.utils.R;
import com.atguigu.gulimall.seckill.service.SeckillService;
import com.atguigu.gulimall.seckill.to.SecKillSkuRedisTo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SeckillController {
    @Autowired
    SeckillService seckillService;

    //找到当前所有可以参与秒杀服务的商品信息
    @GetMapping("/currentSeckillSkus")
    public R getCurrentSeckillSkus() {
        List<SecKillSkuRedisTo> vos = seckillService.getCurrentSeckillSkus();
        return R.ok().setData(vos);
    }

    @GetMapping("/sku/seckill/{skuId}")
    public R getSkuSeckillInfo(@PathVariable("skuId") Long skuId) {
        SecKillSkuRedisTo vo = seckillService.getSkuSeckillInfo(skuId);
        return R.ok().setData(vo);
    }
}
