package com.atguigu.gulimall.ware.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class MyRabbitConfig {

//    @Autowired
//    RabbitTemplate rabbitTemplate;

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // 如果不注释，会产生循环引用
    //    //订制rabbitTemplate
    //    @PostConstruct//当MyRabbitConfig对象创建完成后，再执行这个方法
    //    public void initRabbitTemplate() {
    //        //1. 设置确认回调,只要消息抵达broker，这个方法就会被invoke
    //        rabbitTemplate.setConfirmCallback(new RabbitTemplate.ConfirmCallback() {
    //            @Override
    //            public void confirm(CorrelationData correlationData, boolean ack, String cause) {
    //                System.out.println("==========confirm setConfirmCallback:["+correlationData+"], ack="+ack+", "+", cause=" + cause);
    //            }
    //        });
    //        //2.消息抵达队列的失败确认回调
    //        rabbitTemplate.setReturnCallback(new RabbitTemplate.ReturnCallback() {
    //            //逍遥消息没有投递给指定的queue，就触发这个失败回调
    //            @Override
    //            public void returnedMessage(Message message, int i, String s, String s1, String s2) {
    //                System.out.println("==========fail message:["+message+"], code="+i+", replyText="+s+", exchange="+s1+", routingKey="+s2);
    //            }
    //        });
    //    }

    @Bean
    public Queue stockDelayQueue() {
        //e(String name, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-dead-letter-exchange", "stock-event-exchange");
        arguments.put("x-dead-letter-routing-key", "stock.release.order");
        arguments.put("x-message-ttl", 1000 * 60 * 2);
        return new Queue("stock.delay.queue", true, false, false, arguments);
    }

    @Bean
    public Queue stockReleaseStockQueue() {
        return new Queue("stock.release.stock.queue", true, false, false);
    }

    @Bean
    public Exchange stockEventExchange() {
        return new TopicExchange("stock-event-exchange", true, false);
    }

    @Bean
    public Binding stockReleaseBinding() {
        return new Binding("stock.release.stock.queue", Binding.DestinationType.QUEUE, "stock-event-exchange",
                "stock.release.#", null);
    }

    @Bean
    public Binding orderLockedBinding() {
        return new Binding("stock.delay.queue", Binding.DestinationType.QUEUE, "stock-event-exchange",
                "stock.locked", null);
    }
}
