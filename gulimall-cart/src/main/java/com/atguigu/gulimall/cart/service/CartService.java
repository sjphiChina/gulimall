package com.atguigu.gulimall.cart.service;

import com.atguigu.gulimall.cart.vo.CartItemVo;
import com.atguigu.gulimall.cart.vo.CartVo;

import java.util.List;
import java.util.concurrent.ExecutionException;

public interface CartService {
    CartItemVo addToCart(Long skuId, Integer num) throws ExecutionException, InterruptedException;

    CartItemVo getCartItemVo(Long skuId);

    CartVo getCart() throws ExecutionException, InterruptedException;

    void clearCart(String cartKey);

    //勾选购物项
    void checkItem(Long skuId, Integer check);

    List<CartItemVo> getUserCartItems();
}
