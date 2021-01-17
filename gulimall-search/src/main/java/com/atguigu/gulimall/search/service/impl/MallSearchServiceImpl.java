package com.atguigu.gulimall.search.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.common.to.es.SkuEsModel;
import com.atguigu.gulimall.search.config.GulimallElasticSearchConfig;
import com.atguigu.gulimall.search.constant.EsConstant;
import com.atguigu.gulimall.search.service.MallSearchService;
import com.atguigu.gulimall.search.vo.SearchParam;
import com.atguigu.gulimall.search.vo.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.QueryRescorer;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongRareTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MallSearchServiceImpl implements MallSearchService {

    @Autowired
    RestHighLevelClient restHighLevelClient;

    @Override
    public SearchResult search(SearchParam searchParam) {

        SearchResult searchResult = null;

        SearchRequest searchRequest = buildSearchRequest(searchParam);
        try {
            SearchResponse searchResponse = restHighLevelClient
                    .search(searchRequest, GulimallElasticSearchConfig.COMMON_OPTIONS);
            searchResult = buildSearchResult(searchResponse, searchParam);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return searchResult;
    }

    //video 179
    private SearchRequest buildSearchRequest(SearchParam searchParam) {

        //构建dsl语句
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        //模糊匹配：过滤（按照属性，分类，品牌，价格区间，库存）
        //1.构建bool - query
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        //1.1 must 模糊匹配
        if (!StringUtils.isEmpty(searchParam.getKeyword())) {
            boolQueryBuilder.must(QueryBuilders.matchQuery("skuTitle", searchParam.getKeyword()));
        }
        //1.2 bool filter 三级分类id查询
        if (searchParam.getCatalog3Id() != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("catalogId", searchParam.getCatalog3Id()));
        }
        //1.3 bool filter 品牌id查询
        if (searchParam.getBrandId() != null && searchParam.getBrandId().size() > 0) {
            boolQueryBuilder.filter(QueryBuilders.termsQuery("brandId", searchParam.getBrandId()));
        }
        //1.4 bool filter 按照所有指定的属性查询
        if (searchParam.getAttrs() != null && searchParam.getAttrs().size() > 0) {
            for (String attrStr : searchParam.getAttrs()) {
                //attrs=1_5寸:9寸&attrs=2_16G:8G
                BoolQueryBuilder nestedBoolQuery = QueryBuilders.boolQuery();
                //attr = 1_5寸:9寸
                String[] s = attrStr.split("_");
                String attrId = s[0];//检索的属性id
                String[] attrValues = s[1].split(":");
                nestedBoolQuery.must(QueryBuilders.termQuery("attrs.attrId", attrId));
                nestedBoolQuery.must(QueryBuilders.termsQuery("attrs.attrValue", attrValues));
                //每一个必须都得生成一个nested查询
                NestedQueryBuilder nestedQueryBuilder = QueryBuilders
                        .nestedQuery("attrs", nestedBoolQuery, ScoreMode.None);
                boolQueryBuilder.filter(nestedQueryBuilder);
            }
        }
        //1.5 bool filter 按照是否有库存查询
        if (searchParam.getHasStock() != null )
            boolQueryBuilder.filter(QueryBuilders.termQuery("hasStock", searchParam.getHasStock() == 1));
        //1.6 bool filter 按照价格区间查询
        if (!StringUtils.isEmpty(searchParam.getSkuPrice())) {
            //1_500/_500/500_
            //            "range": {
            //                "skuPrice": {
            //                    "gte": 0,
            //                            "lte": 8000
            //                }
            //            }
            RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery("skuPrice");
            String[] s = searchParam.getSkuPrice().split("_");
            boolean flag = true;
            if (s.length == 2) {
                rangeQueryBuilder.gte(s[0]).lte(s[1]);
            } else if (s.length == 1) {
                if (searchParam.getSkuPrice().startsWith("_")) {
                    rangeQueryBuilder.lt(s[0]);
                } else if (searchParam.getSkuPrice().endsWith("_")) {
                    rangeQueryBuilder.gte(s[0]);
                } else
                    flag = false;
            } else
                flag = false;
            if (flag)
                boolQueryBuilder.filter(rangeQueryBuilder);
        }
        //把所有的条件都拿来进行封装
        sourceBuilder.query(boolQueryBuilder);
        //排序，分页，高亮，
        //2.1 排序
        if (!StringUtils.isEmpty(searchParam.getSort())) {
            String sort = searchParam.getSort();
            //sort=hostScore_asc/desc
            String[] s = sort.split("_");
            SortOrder order = s[1].equalsIgnoreCase("asc") ? SortOrder.ASC : SortOrder.DESC;
            sourceBuilder.sort(s[0], order);
        }
        //2.2 分页 pageSize：5
        // pageNum:1 from:0 size:5 [0,1,2,3,4]
        // pageNum:2 from:5 size:5
        // from = (pageNum - 1)*size
        sourceBuilder.from(((searchParam.getPageNum() == null ? 1 : searchParam.getPageNum()) - 1) *
                EsConstant.PRODUCT_PAGESIZE);
        sourceBuilder.size(EsConstant.PRODUCT_PAGESIZE);
        //2.3 高亮
        if (!StringUtils.isEmpty(searchParam.getKeyword())) {
            HighlightBuilder highlightBuilder = new HighlightBuilder();
            highlightBuilder.field("skuTitle");
            highlightBuilder.preTags("<b style='color:red'>");
            highlightBuilder.postTags("</b>");
            sourceBuilder.highlighter(highlightBuilder);
        }
        //聚合分析
        // 3.1 品牌聚合
        TermsAggregationBuilder brand_agg = AggregationBuilders.terms("brand_agg");
        brand_agg.field("brandId").size(50);
        // 品牌聚合的子聚合
        brand_agg.subAggregation(AggregationBuilders.terms("brand_name_agg").field("brandName.keyword").size(1));
        brand_agg.subAggregation(AggregationBuilders.terms("brand_img_agg").field("brandImg.keyword").size(1));
        sourceBuilder.aggregation(brand_agg);

        // 3.2 分类聚合 catalog_agg
        TermsAggregationBuilder catalog_agg = AggregationBuilders.terms("catalog_agg");
        catalog_agg.field("catalogId").size(50);
        // 品牌聚合的子聚合
        catalog_agg.subAggregation(AggregationBuilders.terms("catalog_name_agg").field("catalogName.keyword").size(1));
        sourceBuilder.aggregation(catalog_agg);

        // TODO 暂时注释以下,issue#6,上面添加了 .keyword
        // 3.3 属性聚合
//        NestedAggregationBuilder attr_agg = AggregationBuilders.nested("attr_agg", "attrs");
//        //聚合出当前酥油的attr_Id
//        TermsAggregationBuilder attr_id_agg = AggregationBuilders.terms("attr_id_agg").field("attrs.attrId");
//        //聚合分析出当前attr_id对应的名字
//        attr_id_agg.subAggregation(AggregationBuilders.terms("attr_name_agg").field("attrs.attrName").size(1));
//        //聚合出当点attr_id对应的所有可能的属性值attrValue
//        attr_id_agg.subAggregation(AggregationBuilders.terms("attr_value_agg").field("attrs.attrValue").size(50));
//        attr_agg.subAggregation(attr_id_agg);
//        sourceBuilder.aggregation(attr_agg);
        log.debug("===============The dsl query is: {}", sourceBuilder.toString());

        SearchRequest searchRequest = new SearchRequest(new String[]{EsConstant.PRODUCT_INDEX}, sourceBuilder);
        return searchRequest;
    }

    private SearchResult buildSearchResult(SearchResponse searchResponse, SearchParam searchParam) {
        SearchResult searchResult = new SearchResult();
        //1. 返回的所有查询到的商品
        SearchHits searchHits = searchResponse.getHits();
        List<SkuEsModel> esModels = new LinkedList<>();
        if (searchHits.getHits() != null && searchHits.getHits().length > 0) {
            for (SearchHit searchHit : searchHits.getHits()) {
                String sourceAsString = searchHit.getSourceAsString();
                SkuEsModel esModel = JSON.parseObject(sourceAsString, SkuEsModel.class);
                if (!StringUtils.isEmpty(searchParam.getKeyword())) {
                    HighlightField skuTitle = searchHit.getHighlightFields().get("skuTitle");
                    String string = skuTitle.getFragments()[0].toString();
                    esModel.setSkuTitle(string);
                }
                esModels.add(esModel);
            }
        }
        searchResult.setProducts(esModels);

        //2. 当前所有商品涉及到的所有属性信息
        List<SearchResult.AttrVo> attrVos = new LinkedList<>();
        ParsedNested attr_agg = searchResponse.getAggregations().get("attr_agg");
        ParsedLongTerms attr_id_agg = attr_agg.getAggregations().get("attr_id_agg");
        for (Terms.Bucket bucket : attr_id_agg.getBuckets()) {
            SearchResult.AttrVo attrVo = new SearchResult.AttrVo();
            //2.1 得到属性id
            long attrId = bucket.getKeyAsNumber().longValue();
            //2.2 属性的名字
            String attrName = ((ParsedStringTerms) bucket.getAggregations().get("attr_name_agg")).getBuckets().get(0)
                    .getKeyAsString();
            //2.3 属性的所有值
            List<String> attrValueList = ((ParsedStringTerms) bucket.getAggregations().get("attr_value_agg"))
                    .getBuckets().stream().map(item -> {
                        String keyAsString = ((Terms.Bucket) item).getKeyAsString();
                        return keyAsString;
                    }).collect(Collectors.toList());
            attrVo.setAttrId(attrId);
            attrVo.setAttrName(attrName);
            attrVo.setAttrValue(attrValueList);
            attrVos.add(attrVo);
        }
        searchResult.setAttrs(attrVos);

        //3. 当前所有商品涉及到的所有品牌信息
        List<SearchResult.BrandVo> brandVos = new LinkedList<>();
        ParsedLongTerms brand_agg = searchResponse.getAggregations().get("brand_agg");
        if (brand_agg != null)
            for (Terms.Bucket bucket : brand_agg.getBuckets()) {
                SearchResult.BrandVo brandVo = new SearchResult.BrandVo();
                //3.1 得到品牌的id
                long brandId = bucket.getKeyAsNumber().longValue();
                //3.2 得到品牌的名字
                String brandName = ((ParsedStringTerms) bucket.getAggregations().get("brand_name_agg")).getBuckets()
                        .get(0).getKeyAsString();
                //3.3 得到品牌的图片
                String brandImg = ((ParsedStringTerms) bucket.getAggregations().get("brand_img_agg")).getBuckets()
                        .get(0).getKeyAsString();
                brandVo.setBrandId(brandId);
                brandVo.setBrandName(brandName);
                brandVo.setBrandImg(brandImg);
                brandVos.add(brandVo);
            }
        searchResult.setBrands(brandVos);

        //4. 当前所有商品涉及到的所有分类信息
        ParsedLongTerms catalog_agg = searchResponse.getAggregations().get("catalog_agg");
        List<SearchResult.CatalogVo> catalogVos = new LinkedList<>();
        List<? extends Terms.Bucket> buckets = catalog_agg.getBuckets();
        for (Terms.Bucket bucket : buckets) {
            SearchResult.CatalogVo catalogVo = new SearchResult.CatalogVo();
            //得到分类id
            String keyAsString = bucket.getKeyAsString();
            catalogVo.setCatalogId(Long.parseLong(keyAsString));
            //得到分类名
            ParsedStringTerms catalog_name_agg = bucket.getAggregations().get("catalog_name_agg");
            String catalog_name = catalog_name_agg.getBuckets().get(0).getKeyAsString();
            catalogVo.setCatalogName(catalog_name);
            catalogVos.add(catalogVo);
        }
        searchResult.setCatalogs(catalogVos);

        //5. 当前页码
        searchResult.setPageNum(searchParam.getPageNum());
        //6. 总记录数
        long total = searchHits.getTotalHits().value;
        searchResult.setTotal(total);
        //7. 总页码 - 计算
        int totalPages = (int) total % EsConstant.PRODUCT_PAGESIZE == 0 ? (int) total / EsConstant.PRODUCT_PAGESIZE :
                ((int) total / EsConstant.PRODUCT_PAGESIZE + 1);
        searchResult.setTotalPages(totalPages);
        return searchResult;
    }
}
