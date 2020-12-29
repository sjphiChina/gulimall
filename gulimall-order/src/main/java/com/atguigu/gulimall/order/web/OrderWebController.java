package com.atguigu.gulimall.order.web;

import com.atguigu.common.vo.MemberVo;
import com.atguigu.gulimall.order.feign.MemberFeignService;
import com.atguigu.gulimall.order.interceptor.LoginUserInterceptor;
import com.atguigu.gulimall.order.service.OrderService;
import com.atguigu.gulimall.order.vo.OrderConfirmVo;
import com.atguigu.gulimall.order.vo.OrderSubmitVo;
import com.atguigu.gulimall.order.vo.SubmitOrderResponseVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.concurrent.ExecutionException;

@Controller
public class OrderWebController {

    @Autowired
    OrderService orderService;

    @GetMapping("/toTrade")
    public String toTrade(Model model) throws ExecutionException, InterruptedException {
        MemberVo memberVo = LoginUserInterceptor.loginUser.get();
        //得到所有收货地址
        OrderConfirmVo confirmVo = orderService.confirmOrder();
        model.addAttribute("orderConfirmData", confirmVo);
        //得到当前所有购物项
        return "confirm";
    }

    @PostMapping("/submitOrder")
    public String submitOrder(OrderSubmitVo vo) {
        SubmitOrderResponseVo responseVo = orderService.submitOrder(vo);

        if (responseVo.getCode() == 0) {

            return "pay";
        } else {
            return "redirect:http://order.gulimall.com/toTrade";
        }


        //创建订单

        //验证令牌

        //锁库存

        //下单成功则转到支付页面

        //下单失败

    }
}
