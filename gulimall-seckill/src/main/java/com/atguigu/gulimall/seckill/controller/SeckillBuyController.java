package com.atguigu.gulimall.seckill.controller;

import com.atguigu.gulimall.seckill.service.SeckillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class SeckillBuyController {

    @Autowired
    SeckillService seckillService;

    @GetMapping("/kill")
    public String getSkuSeckillInfo(@RequestParam("killId") String killId, @RequestParam("key") String key,
                                    @RequestParam("num") Integer num, Model model) {
        String orderSn = seckillService.kill(killId, key, num);
        model.addAttribute("orderSn", orderSn);
        return "success";
    }
}
