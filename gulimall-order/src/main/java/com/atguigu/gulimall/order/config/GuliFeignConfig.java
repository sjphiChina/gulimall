package com.atguigu.gulimall.order.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

@Configuration
public class GuliFeignConfig {

    @Bean
    public RequestInterceptor requestInterceptor(){
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                System.out.println("feign在远程之前先调用此方法");
                //拿到刚进来的这个请求
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest request = attributes.getRequest();//老请求
                    if (request != null) {
                        //同步请求头数据cookie
                        String cookie = request.getHeader("Cookie");
                        //给新请求同步了老请求
                        template.header("Cookie", cookie);
                    } else
                        System.out.println("!!!!!!!!(request == null)");
                }
            }
        };
    }
}
