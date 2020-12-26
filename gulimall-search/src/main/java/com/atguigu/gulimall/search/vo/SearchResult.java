package com.atguigu.gulimall.search.vo;

import com.atguigu.common.to.es.SkuEsModel;
import lombok.Data;

import java.util.List;

@Data
public class SearchResult {

    private List<SkuEsModel> products;

    //以下是分页信息
    private Integer pageNum;
    private Long total;
    private Integer totalPages;

    private List<BrandVo> brands;
    private List<CatalogVo> catalogs;
    private List<AttrVo> attrs;

    //以上是返回给页面的信息
    @Data
    public static class BrandVo{

        private Long brandId;
        private String brandName;
        private String brandImg;
    }

    @Data
    public static class CatalogVo{

        private Long catalogId;
        private String catalogName;
    }

    @Data
    public static class AttrVo{

        private Long attrId;
        private String attrName;
        private List<String> attrValue;
    }
}
