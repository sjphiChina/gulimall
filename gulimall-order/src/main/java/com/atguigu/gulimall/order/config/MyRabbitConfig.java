package com.atguigu.gulimall.order.config;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Configuration
public class MyRabbitConfig {

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    //订制rabbitTemplate
    @PostConstruct//当MyRabbitConfig对象创建完成后，再执行这个方法
    public void initRabbitTemplate() {
        //1. 设置确认回调,只要消息抵达broker，这个方法就会被invoke
        rabbitTemplate.setConfirmCallback(new RabbitTemplate.ConfirmCallback() {
            @Override
            public void confirm(CorrelationData correlationData, boolean ack, String cause) {
                System.out.println("==========confirm setConfirmCallback:["+correlationData+"], ack="+ack+", "+", cause=" + cause);
            }
        });
        //2.消息抵达队列的失败确认回调
        rabbitTemplate.setReturnCallback(new RabbitTemplate.ReturnCallback() {
            //逍遥消息没有投递给指定的queue，就触发这个失败回调
            @Override
            public void returnedMessage(Message message, int i, String s, String s1, String s2) {
                System.out.println("==========fail message:["+message+"], code="+i+", replyText="+s+", exchange="+s1+", routingKey="+s2);
            }
        });
    }
}
