package com.atguigu.common.exception;

public class NoStockException extends RuntimeException{
    private long skuId;

    public NoStockException(String msg) {
        super(msg);
    }
    public NoStockException(long skuId) {
        super("商品skuId="+skuId+"没有库存了");
        this.skuId = skuId;
    }

    public long getSkuId() {
        return skuId;
    }

    public void setSkuId(long skuId) {
        this.skuId = skuId;
    }
}
