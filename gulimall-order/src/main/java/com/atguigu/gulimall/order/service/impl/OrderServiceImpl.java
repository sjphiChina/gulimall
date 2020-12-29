package com.atguigu.gulimall.order.service.impl;

import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.utils.R;
import com.atguigu.common.vo.MemberVo;
import com.atguigu.gulimall.order.feign.CartFeignService;
import com.atguigu.gulimall.order.feign.MemberFeignService;
import com.atguigu.gulimall.order.feign.WareFeignService;
import com.atguigu.gulimall.order.interceptor.LoginUserInterceptor;
import com.atguigu.gulimall.order.vo.MemberAddressVo;
import com.atguigu.gulimall.order.vo.OrderConfirmVo;
import com.atguigu.gulimall.order.vo.OrderItemVo;
import com.atguigu.gulimall.order.vo.SkuStockVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.Member;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.order.dao.OrderDao;
import com.atguigu.gulimall.order.entity.OrderEntity;
import com.atguigu.gulimall.order.service.OrderService;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;


@Service("orderService")
public class OrderServiceImpl extends ServiceImpl<OrderDao, OrderEntity> implements OrderService {

    @Autowired
    MemberFeignService memberFeignService;

    @Autowired
    CartFeignService cartFeignService;

    @Autowired
    ThreadPoolExecutor threadPoolExecutor;

    @Autowired
    WareFeignService wareFeignService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<OrderEntity> page = this.page(
                new Query<OrderEntity>().getPage(params),
                new QueryWrapper<OrderEntity>()
        );

        return new PageUtils(page);
    }

    @Override
    public OrderConfirmVo confirmOrder() throws ExecutionException, InterruptedException {
        OrderConfirmVo confirmVo = new OrderConfirmVo();

        // TIP 解决feign的请求头丢失问题
        //此处需解决feign的请求头丢失问题，即远程调用时没有cookie
        //为此，添加了GuliFeignConfig，为feign添加了interceptor，参见GuliFeignConfig
        MemberVo memberVo = LoginUserInterceptor.loginUser.get();

        // TIP 解决feign的异步编排导致请求头丢失问题，如下
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();

        CompletableFuture<Void> getAddressFuture = CompletableFuture.runAsync(() -> {
            //将主线程的attribute传递给子线程
            RequestContextHolder.setRequestAttributes(attributes);
            //查询所有的收货地址的列表
            List<MemberAddressVo> address = memberFeignService.getAddress(memberVo.getId());
            confirmVo.setAddressVoList(address);
        }, threadPoolExecutor);

        CompletableFuture<Void> cartFuture = CompletableFuture.runAsync(() -> {
            RequestContextHolder.setRequestAttributes(attributes);
            //查询购物车中的所有选中项
            List<OrderItemVo> itemVos = cartFeignService.getCurrentUserCartItems();
            confirmVo.setItemVos(itemVos);

            //计算总价
            BigDecimal total = new BigDecimal("0");
            if (itemVos != null) {
                for (OrderItemVo itemVo : itemVos) {
                    BigDecimal multiply = itemVo.getPrice().multiply(new BigDecimal(itemVo.getCount().toString()));
                    total = total.add(multiply);
                }
            }
            confirmVo.setTotal(total);
            confirmVo.setPayPrice(total);
        }, threadPoolExecutor).thenRunAsync(() -> {
            // TIP 这里承接上一个CompletableFuture，可到所有sku的库存信息，然后映射成一个map，存入confirmVo
            List<OrderItemVo> itemVos = confirmVo.getItemVos();
            List<Long> collect = itemVos.stream().map(item -> item.getSkuId()).collect(Collectors.toList());
            R hasStock = wareFeignService.getSkusHasStock(collect);
            List<SkuStockVo> data = hasStock.getData(new TypeReference<List<SkuStockVo>>() {});
            if (data != null) {
                Map<Long, Boolean> map = data.stream()
                        .collect(Collectors.toMap(SkuStockVo::getSkuId, SkuStockVo::getHasStock));
                confirmVo.setStocks(map);
            }
        }, threadPoolExecutor);


        //查询用户几分
        Integer integration = memberVo.getIntegration();
        confirmVo.setIntegration(integration);

        CompletableFuture.allOf(getAddressFuture,cartFuture).get();


        //防重复令牌
        return confirmVo;
    }

}