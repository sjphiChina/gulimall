package com.atguigu.gulimall.ware.config;

import com.baomidou.mybatisplus.extension.plugins.PaginationInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//@EnableTransactionManagement
@MapperScan("com.atguigu.gulimall.ware.dao")
@Configuration
public class WareMyBatisConfig {

    //引入分页插件
    @Bean
    public PaginationInterceptor paginationInterceptor() {
        PaginationInterceptor paginationInterceptor = new PaginationInterceptor();
        // 设置请求的页面大于最大页后操作， true调回到首页，false 继续请求  默认false
//        paginationInterceptor.setOverflow(true);
//        // 设置最大单页限制数量，默认 500 条，-1 不受限制
//        paginationInterceptor.setLimit(1000);
        return paginationInterceptor;
    }

    //目前采用基于RabbitMQ的消息最终一致性来保证分布式事务，注销Seata的使用
    //    @Autowired
    //    DataSourceProperties dataSourceProperties;

    //    @Bean
    //    public DataSource dataSource(DataSourceProperties dataSourceProperties) {
    //        HikariDataSource dataSource = dataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    //        if (StringUtils.hasText(dataSourceProperties.getName())) {
    //            dataSource.setPoolName(dataSourceProperties.getName());
    //        }
    //        return new DataSourceProxy(dataSource);
    //    }
}
