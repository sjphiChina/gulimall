package com.atguigu.gulimall.order.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class OrderWebController {

    @GetMapping("/toTrade")
    public String toTrade() {

        return "confirm";
    }
}
