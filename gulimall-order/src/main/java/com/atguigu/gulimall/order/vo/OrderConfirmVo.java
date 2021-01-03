package com.atguigu.gulimall.order.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class OrderConfirmVo {

    //收货地址
    List<MemberAddressVo> addressVoList;

    //所有选中的购物项
    List<OrderItemVo> itemVos;

    //发票信息

    //优惠券信息
    Integer integration;

    BigDecimal total;//订单zonge

    BigDecimal payPrice;//应付价格

    //防重复提交令牌
    String orderToken;

    //保存每个sku的库存信息
    Map<Long, Boolean> stocks;
}
