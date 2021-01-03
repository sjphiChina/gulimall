package com.atguigu.gulimall.search.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.common.to.es.SkuEsModel;
import com.atguigu.gulimall.search.config.GulimallElasticSearchConfig;
import com.atguigu.gulimall.search.constant.EsConstant;
import com.atguigu.gulimall.search.service.ProductSaveService;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProductSaveServiceImpl implements ProductSaveService {

    @Autowired
    RestHighLevelClient restHighLevelClient;

    @Override
    public boolean productStatusUp(List<SkuEsModel> skuEsModels) throws IOException {
        // 给es中建立索引: product
        // 建立映射关系

        // 给es中保存数据
        // (BulkRequest bulkRequest, RequestOptions options)
        BulkRequest bulkRequest = new BulkRequest();
        for (SkuEsModel model: skuEsModels) {
            IndexRequest indexRequest = new IndexRequest(EsConstant.PRODUCT_INDEX);
            indexRequest.id(model.getSkuId().toString());
            String s = JSON.toJSONString(model);
            indexRequest.source(s, XContentType.JSON);
            bulkRequest.add(indexRequest);
        }

        BulkResponse bulkItemResponse = restHighLevelClient.bulk(bulkRequest, GulimallElasticSearchConfig.COMMON_OPTIONS);
        boolean result = bulkItemResponse.hasFailures();
        List<String> collect = Arrays.stream(bulkItemResponse.getItems()).map(item -> {
            return item.getId();
        }).collect(Collectors.toList());
        log.error("商品搜索上架成功: {}, 返回的数据 {}", collect, bulkItemResponse.toString());
        return !result;
    }
}
