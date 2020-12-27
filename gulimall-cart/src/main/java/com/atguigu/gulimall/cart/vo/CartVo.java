package com.atguigu.gulimall.cart.vo;

import java.math.BigDecimal;
import java.util.List;

// 需要计算的属性，必须重写get方法，保证每次得到最新的数据
public class CartVo {
    List<CartItemVo> items;
//    private Integer countNum;
//    private Integer countType;
//    private BigDecimal totalAmount;
    private BigDecimal reduce = new BigDecimal("0");

    public List<CartItemVo> getItems() {
        return items;
    }

    public void setItems(List<CartItemVo> items) {
        this.items = items;
    }

    public Integer getCountNum() {
        int count = 0;
        if (items != null && items.size() > 0) {
            for (CartItemVo itemVo: items) {
                count += itemVo.getCount();
            }
        }
        return count;
    }

    public Integer getCountType() {
        int count = 0;
        if (items != null && items.size() > 0) {
            for (CartItemVo itemVo: items) {
                count += 1;
            }
        }
        return count;
    }

    public BigDecimal getTotalAmount() {
        BigDecimal amount = new BigDecimal("0");
        // 1. 计算购物享总价
        if (items != null && items.size() > 0) {
            for (CartItemVo itemVo: items) {
                BigDecimal totalPrice = itemVo.getTotalPrice();
                amount = amount.add(totalPrice);
            }
        }
        // 2. 减去优惠总价
        BigDecimal finalAmount = amount.subtract(getReduce());
        return finalAmount;
    }

    public BigDecimal getReduce() {
        return reduce;
    }

    public void setReduce(BigDecimal reduce) {
        this.reduce = reduce;
    }
}
