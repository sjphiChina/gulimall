package com.atguigu.gulimall.order.web;

import com.atguigu.common.vo.MemberVo;
import com.atguigu.gulimall.order.feign.MemberFeignService;
import com.atguigu.gulimall.order.interceptor.LoginUserInterceptor;
import com.atguigu.gulimall.order.service.OrderService;
import com.atguigu.gulimall.order.vo.OrderConfirmVo;
import com.atguigu.gulimall.order.vo.OrderSubmitVo;
import com.atguigu.gulimall.order.vo.SubmitOrderResponseVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.concurrent.ExecutionException;

@Slf4j
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
    public String submitOrder(OrderSubmitVo vo, Model model, RedirectAttributes redirectAttributes) {
        SubmitOrderResponseVo responseVo = orderService.submitOrder(vo);
        log.info("订单提交的数据：{}", vo);
        if (responseVo.getCode() == 0) {
            model.addAttribute("submitOrderResponse", responseVo);
            return "pay";
        } else {
            String msg = "下单失败: ";
            switch (responseVo.getCode()) {
                case 1: msg += "订单信息过期，请刷新再提交";break;
                case 2: msg += "订单商品价格发生变化，请确认再提交";break;
                case 3: msg += "库存锁定失败，商品库存不足 ";break;
            }
            // TIP 给session中存入一次性数据
            redirectAttributes.addFlashAttribute("msg", msg);
            return "redirect:http://order.gulimall.com/toTrade";
        }


        //创建订单

        //验证令牌

        //锁库存

        //下单成功则转到支付页面

        //下单失败

    }
}
