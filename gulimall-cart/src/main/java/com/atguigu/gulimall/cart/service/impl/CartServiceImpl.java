package com.atguigu.gulimall.cart.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.cart.feign.ProductFeignService;
import com.atguigu.gulimall.cart.interceptor.CartInterceptor;
import com.atguigu.gulimall.cart.service.CartService;
import com.atguigu.gulimall.cart.vo.CartItemVo;
import com.atguigu.gulimall.cart.vo.CartVo;
import com.atguigu.gulimall.cart.vo.SkuInfoVo;
import com.atguigu.gulimall.cart.vo.UserInfoTo;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

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

    @Override
    public CartItemVo getCartItemVo(Long skuId) {
        BoundHashOperations<String, Object, Object> ops = getCartOps();
        String str = (String) ops.get(skuId.toString());
        return JSON.parseObject(str, CartItemVo.class);
    }

    @Override
    public CartVo getCart() throws ExecutionException, InterruptedException {
        CartVo cartVo = new CartVo();
        UserInfoTo userInfoTo = CartInterceptor.threadLocal.get();
        if (userInfoTo.getUserId() != null) {
            String cartKey = CART_PREFIX + userInfoTo.getUserId();
            //如果临时购物车的数据还没有合并，那就合并
            String tempCartKey = CART_PREFIX+userInfoTo.getUserKey();
            List<CartItemVo> list = getCartItems(tempCartKey);
            if (list!= null) {
                for (CartItemVo itemVo : list) {
                    addToCart(itemVo.getSkuId(), itemVo.getCount());
                }
                //情况临时购物车
                clearCart(tempCartKey);
            }
            //获取登录后的购物车数据（包含临时购物车的数据）
            List<CartItemVo> cartItemVos = getCartItems(cartKey);
            cartVo.setItems(cartItemVos);
        } else {
            // 没登录，则获取临时购物车的所有购物享
            String cartKey = CART_PREFIX + userInfoTo.getUserKey();
            List<CartItemVo> list = getCartItems(cartKey);
            cartVo.setItems(list);
        }
        return cartVo;
    }

    //获取要操作的购物车
    private BoundHashOperations<String, Object, Object> getCartOps() {
        UserInfoTo userInfoTo = CartInterceptor.threadLocal.get();
        String carKey;
        if (userInfoTo.getUserId() != null) {
            carKey = CART_PREFIX + userInfoTo.getUserId();
        } else {
            carKey = CART_PREFIX + userInfoTo.getUserKey();
        }
        return redisTemplate.boundHashOps(carKey);
    }

    private List<CartItemVo> getCartItems(String cartKey) {
        BoundHashOperations<String, Object, Object> operations = redisTemplate.boundHashOps(cartKey);
        List<Object> values = operations.values();
        if (values != null && values.size() > 0) {
            List<CartItemVo> collect = values.stream().map((obj) -> {
                String str = (String) obj;
                CartItemVo cartItemVo = JSON.parseObject(str, CartItemVo.class);
                return cartItemVo;
            }).collect(Collectors.toList());
            return collect;
        }
        return null;
    }

    @Override
    public void clearCart(String cartKey) {
        redisTemplate.delete(cartKey);
    }

    @Override
    public void checkItem(Long skuId, Integer check) {
        BoundHashOperations<String, Object, Object> cartOps = getCartOps();
        CartItemVo cartItemVo = getCartItemVo(skuId);
        cartItemVo.setCheck(check == 1);
        String s = JSON.toJSONString(cartItemVo);
        cartOps.put(skuId.toString(), s);
    }

    @Override
    public List<CartItemVo> getUserCartItems() {
        UserInfoTo userInfoTo = CartInterceptor.threadLocal.get();
        if (userInfoTo.getUserId() == null)
            return null;

        String cartKey = CART_PREFIX + userInfoTo.getUserId();
        List<CartItemVo> cartItemVoList = getCartItems(cartKey);
        assert cartItemVoList != null;
        return cartItemVoList.stream().filter(CartItemVo::getCheck).peek(cartItemVo -> {
            //更新为db中的最新价格而不是cache中的价格
            R price = productFeignService.getPrice(cartItemVo.getSkuId());
            String data = (String) price.get("data");
            cartItemVo.setPrice(new BigDecimal(data));
        }).collect(Collectors.toList());
    }
}
