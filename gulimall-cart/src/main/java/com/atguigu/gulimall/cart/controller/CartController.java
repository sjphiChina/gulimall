package com.atguigu.gulimall.cart.controller;

import com.atguigu.gulimall.cart.interceptor.CartInterceptor;
import com.atguigu.gulimall.cart.service.CartService;
import com.atguigu.gulimall.cart.vo.CartItemVo;
import com.atguigu.gulimall.cart.vo.CartVo;
import com.atguigu.gulimall.cart.vo.UserInfoTo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.concurrent.ExecutionException;

@Slf4j
@Controller
public class CartController {

    @Autowired
    CartService cartService;

    @GetMapping("/currentUserCartItems")
    @ResponseBody// TIP 因为返回一个json数据，所以用ResponseBody
    public List<CartItemVo> getCurrentUserCartItems() {
        return cartService.getUserCartItems();
    }

    @GetMapping("/cart.html")
    public String carListPage(Model model) throws ExecutionException, InterruptedException {
        UserInfoTo userInfoTo = CartInterceptor.threadLocal.get();
        log.info("Current Thread userinfo: {}", userInfoTo.toString());
        CartVo cart = cartService.getCart();
        model.addAttribute("cart", cart);
        return "cartList";
    }

    @GetMapping("/addToCart")
    public String addToCart(@RequestParam("skuId") Long skuId, @RequestParam("num") Integer num, RedirectAttributes ra)
            throws ExecutionException, InterruptedException {

        cartService.addToCart(skuId, num);
        ra.addAttribute("skuId", skuId);
        return "redirect:http://cart.gulimall.com/addToCartSuccess.html";
    }

    //通过重定向，每次刷新success页面，不用担心重复添加
    @GetMapping("/addToCartSuccess.html")
    public String addToCartSuccessPage(@RequestParam("skuId") Long skuId, Model model) {
        //重定向到成功页面，再次查询购物车数据即可
        CartItemVo itemVo = cartService.getCartItemVo(skuId);
        model.addAttribute("item", itemVo);
        return "success";
    }

    @GetMapping("/checkItem")
    public String checkItem(@RequestParam("skuId") Long skuId, @RequestParam("check") Integer check) {
        cartService.checkItem(skuId, check);
        return "redirect:http://cart.gulimall.com/cart.html";
    }



}
