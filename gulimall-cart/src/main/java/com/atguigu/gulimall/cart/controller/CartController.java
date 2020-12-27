package com.atguigu.gulimall.cart.controller;

import com.atguigu.gulimall.cart.interceptor.CartInterceptor;
import com.atguigu.gulimall.cart.vo.UserInfoTo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Slf4j
@Controller
public class CartController {

    @GetMapping("/cart.html")
    public String carListPage(){
        UserInfoTo userInfoTo = CartInterceptor.threadLocal.get();

        log.info("Current Thread userinfo: {}", userInfoTo.toString());
        return "cartList";
    }

    @GetMapping("/addToCart")
    public String addToCart(){
        UserInfoTo userInfoTo = CartInterceptor.threadLocal.get();

        log.info("Current Thread userinfo: {}", userInfoTo.toString());
        return "success";
    }
}
