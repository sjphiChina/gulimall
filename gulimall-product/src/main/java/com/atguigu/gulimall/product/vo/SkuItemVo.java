package com.atguigu.gulimall.product.vo;

import com.atguigu.gulimall.product.entity.SkuImagesEntity;
import com.atguigu.gulimall.product.entity.SkuInfoEntity;
import com.atguigu.gulimall.product.entity.SpuInfoDescEntity;
import lombok.Data;

import java.util.List;

@Data
public class SkuItemVo {

    // sku的基本信息
    SkuInfoEntity skuInfoEntity;

    boolean hasStock = true;

    // sku的图片信息
    List<SkuImagesEntity> images;

    // spu的销售属性组合
    List<SkuItemSaleAttrVo> saleAttr;

    // spu的介绍
    SpuInfoDescEntity desp;

    // spu的规格参数信息
    List<SpuItemAttrGroupVo> groupAttrs;

    // 秒杀信息
    SeckillInfoVo seckillInfoVo;
}
