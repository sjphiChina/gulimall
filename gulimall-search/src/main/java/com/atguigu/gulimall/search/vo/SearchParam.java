package com.atguigu.gulimall.search.vo;

import lombok.Data;

import java.util.List;

@Data
public class SearchParam {

    //keyword=小米 &sort=saleCount_desc/asc&hasStock=0/1&skuPrice=400_1900&brandId=1&catalog3Id=1&attrs=1_3G:4G:5G&attrs=2_骁龙845&attrs=4_高清屏

    private String keyword;
    private Long catalog3Id;

    private String sort;

    //过滤条件
    private Integer hasStock;
    private String skuPrice;
    private List<Long> brandId;
    private List<String> attrs;//按照属性筛选
    private Integer pageNum;


}
