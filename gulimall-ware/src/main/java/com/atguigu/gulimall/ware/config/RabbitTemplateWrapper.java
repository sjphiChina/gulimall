package com.atguigu.gulimall.ware.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Slf4j
@Component
public class RabbitTemplateWrapper extends RabbitTemplate {
    //订制rabbitTemplate
    @PostConstruct//当MyRabbitConfig对象创建完成后，再执行这个方法
    public void initRabbitTemplate() {
                //1. 设置确认回调,只要消息抵达broker，这个方法就会被invoke
                this.setConfirmCallback(new RabbitTemplate.ConfirmCallback() {
                    @Override
                    public void confirm(CorrelationData correlationData, boolean ack, String cause) {
                        //服务器收到了消息
                        log.info("服务器收到了消息 setConfirmCallback:["+correlationData+"], ack="+ack+", "+", cause=" + cause);
                    }
                });
                //2.消息抵达队列的失败确认回调
                this.setReturnCallback(new RabbitTemplate.ReturnCallback() {
                    //逍遥消息没有投递给指定的queue，就触发这个失败回调
                    @Override
                    public void returnedMessage(Message message, int i, String s, String s1, String s2) {
                        //服务器没能将消息存入队列
                        log.info("服务器没能将消息存入队列 message:["+message+"], code="+i+", replyText="+s+", exchange="+s1+", routingKey="+s2);
                    }
                });
    }
}
