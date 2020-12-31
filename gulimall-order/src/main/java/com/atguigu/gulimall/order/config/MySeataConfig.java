package com.atguigu.gulimall.order.config;

import com.zaxxer.hikari.HikariDataSource;
import io.seata.rm.datasource.DataSourceProxy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

@Configuration
public class MySeataConfig {

    //目前采用基于RabbitMQ的消息最终一致性来保证分布式事务，注销Seata的使用
    //    @Autowired
    //    DataSourceProperties dataSourceProperties;
    //
    //    @Bean
    //    public DataSource dataSource(DataSourceProperties dataSourceProperties) {
    //        HikariDataSource dataSource = dataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    //        if (StringUtils.hasText(dataSourceProperties.getName())) {
    //            dataSource.setPoolName(dataSourceProperties.getName());
    //        }
    //        return new DataSourceProxy(dataSource);
    //    }
}
