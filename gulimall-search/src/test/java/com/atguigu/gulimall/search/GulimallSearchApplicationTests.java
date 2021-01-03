package com.atguigu.gulimall.search;

import com.alibaba.fastjson.JSON;
import com.atguigu.gulimall.search.config.GulimallElasticSearchConfig;
import lombok.Data;
import org.apache.lucene.index.IndexReader;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.util.Map;

@RunWith(SpringRunner.class)
@SpringBootTest
public class GulimallSearchApplicationTests {

	@Autowired
	RestHighLevelClient client;

	@Test
	public void searchData() throws IOException {
		SearchRequest searchRequest = new SearchRequest();
		searchRequest.indices("companydatabase");
		SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
		sourceBuilder.query(QueryBuilders.matchQuery("LastName", "WENIG"));
		System.out.println(sourceBuilder.toString());
		searchRequest.source(sourceBuilder);

		SearchResponse searchResponse = client.search(searchRequest, GulimallElasticSearchConfig.COMMON_OPTIONS);
		System.out.println(searchResponse);
		Map map = JSON.parseObject(searchResponse.toString(), Map.class);
	}

	@Test
	public void indexData() throws IOException {
		IndexRequest indexRequest = new IndexRequest("users");
		indexRequest.id("1");
		//indexRequest.source("userName", "Zhangsan", "age", 18, "gender", "男");
		User user = new User();
		user.setUserName("Zhangsan");
		user.setAge(18);
		user.setGender("男");
		String str = JSON.toJSONString(user);
		indexRequest.source(str, XContentType.JSON);
		IndexResponse indexResponse = client.index(indexRequest, GulimallElasticSearchConfig.COMMON_OPTIONS);
		System.out.println(indexResponse);
	}

	@Data
	class User {
		private String userName;
		private String gender;
		private Integer age;
	}

	@Test
	public void contextLoads() {
		System.out.println(client);
	}

}
