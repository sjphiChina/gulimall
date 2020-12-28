package com.atguigu.gulimall.cart.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.cart.feign.ProductFeignService;
import com.atguigu.gulimall.cart.interceptor.CartInterceptor;
import com.atguigu.gulimall.cart.service.CartService;
import com.atguigu.gulimall.cart.vo.CartItemVo;
import com.atguigu.gulimall.cart.vo.SkuInfoVo;
import com.atguigu.gulimall.cart.vo.UserInfoTo;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

@Service
public class CartServiceImpl implements CartService {

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    ProductFeignService productFeignService;

    @Autowired
    ThreadPoolExecutor threadPoolExecutor;

    private final String CART_PREFIX = "gulimall:cart";

    @Override
    public CartItemVo addToCart(Long skuId, Integer num) throws ExecutionException, InterruptedException {
        BoundHashOperations<String, Object, Object> ops = getCartOps();

        String res = (String) ops.get(skuId.toString());
        if (StringUtils.isEmpty(res)) {
            CartItemVo cartItemVo = new CartItemVo();
            CompletableFuture<Void> getSkuInfoTask = CompletableFuture.runAsync(() -> {
                R skuInfo = productFeignService.getSkuInfo(skuId);
                SkuInfoVo infoVo = skuInfo.getData("skuInfo", new TypeReference<SkuInfoVo>() {});
                //将商品添加到购物车
                cartItemVo.setCheck(true);
                cartItemVo.setCount(1);
                cartItemVo.setImage(infoVo.getSkuDefaultImg());
                cartItemVo.setTitle(infoVo.getSkuTitle());
                cartItemVo.setSkuId(infoVo.getSkuId());
                cartItemVo.setPrice(infoVo.getPrice());
            }, threadPoolExecutor);

            CompletableFuture<Void> getSkuAttrTask = CompletableFuture.runAsync(() -> {
                //查询sku的组合信息
                List<String> list = productFeignService.getSkuSaleAttrValues(skuId);
                cartItemVo.setSkuAttr(list);
            }, threadPoolExecutor);

            CompletableFuture.allOf(getSkuInfoTask, getSkuAttrTask).get();
            String s = JSON.toJSONString(cartItemVo);
            ops.put(skuId.toString(), s);
            return cartItemVo;
        }
        CartItemVo cartItemVo = JSON.parseObject(res, CartItemVo.class);
        cartItemVo.setCount(cartItemVo.getCount() + num);
        String s = JSON.toJSONString(cartItemVo);
        ops.put(skuId.toString(), s);
        return cartItemVo;
    }

    //获取要操作的购物车
    private BoundHashOperations<String, Object, Object> getCartOps() {
        UserInfoTo userInfoTo = CartInterceptor.threadLocal.get();
        String carKey = "";
        if (userInfoTo.getUserId() != null) {
            carKey = CART_PREFIX + userInfoTo.getUserId();
        } else {
            carKey = CART_PREFIX + userInfoTo.getUserKey();
        }
        BoundHashOperations<String, Object, Object> operations = redisTemplate.boundHashOps(carKey);
        return operations;
    }
}
