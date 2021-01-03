package com.atguigu.gulimall.order.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

// KNOW 设置RabbitMQ的延时队列
@Slf4j
@Configuration
public class MyRabbitConfig {

    @Bean
    @ConditionalOnSingleCandidate(ConnectionFactory.class)
    @ConditionalOnMissingBean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, RabbitProperties propertiesA) {
        PropertyMapper map = PropertyMapper.get();
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        template.setMandatory(true);
        RabbitProperties.Template properties = propertiesA.getTemplate();
        map.from(properties::getReceiveTimeout).whenNonNull().as(Duration::toMillis).to(template::setReceiveTimeout);
        map.from(properties::getReplyTimeout).whenNonNull().as(Duration::toMillis).to(template::setReplyTimeout);
        map.from(properties::getExchange).to(template::setExchange);
        map.from(properties::getRoutingKey).to(template::setRoutingKey);
        map.from(properties::getDefaultReceiveQueue).whenNonNull().to(template::setDefaultReceiveQueue);
        //1. 设置确认回调,只要消息抵达broker，这个方法就会被invoke
        template.setConfirmCallback(new RabbitTemplate.ConfirmCallback() {
            @Override
            public void confirm(CorrelationData correlationData, boolean ack, String cause) {
                //服务器收到了消息
                log.info("服务器收到了消息 setConfirmCallback:[" + correlationData + "], ack=" + ack + ", " + ", cause=" +
                        cause);
            }
        });
        //2.消息抵达队列的失败确认回调
        template.setReturnCallback(new RabbitTemplate.ReturnCallback() {
            //逍遥消息没有投递给指定的queue，就触发这个失败回调
            @Override
            public void returnedMessage(Message message, int i, String s, String s1, String s2) {
                //服务器没能将消息存入队列
                log.info("服务器没能将消息存入队列 message:[" + message + "], code=" + i + ", replyText=" + s + ", exchange=" + s1 +
                        ", routingKey=" + s2);
            }
        });
        return template;
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }


    //下面的做法目前会产生循环引用RabbitTemplate
    //    @Autowired
    //    RabbitTemplate rabbitTemplate;

    //订制rabbitTemplate
    //    @PostConstruct//当MyRabbitConfig对象创建完成后，再执行这个方法
    //    public void initRabbitTemplate() {
    //        //1. 设置确认回调,只要消息抵达broker，这个方法就会被invoke
    //        rabbitTemplate.setConfirmCallback(new RabbitTemplate.ConfirmCallback() {
    //            @Override
    //            public void confirm(CorrelationData correlationData, boolean ack, String cause) {
    //                //服务器收到了消息
    //                log.info("服务器收到了消息 setConfirmCallback:["+correlationData+"], ack="+ack+", "+", cause=" + cause);
    //            }
    //        });
    //        //2.消息抵达队列的失败确认回调
    //        rabbitTemplate.setReturnCallback(new RabbitTemplate.ReturnCallback() {
    //            //逍遥消息没有投递给指定的queue，就触发这个失败回调
    //            @Override
    //            public void returnedMessage(Message message, int i, String s, String s1, String s2) {
    //                //服务器没能将消息存入队列
    //                log.info("服务器没能将消息存入队列 message:["+message+"], code="+i+", replyText="+s+", exchange="+s1+", routingKey="+s2);
    //            }
    //        });
    //    }

    @Bean
    public Queue orderDelayQueue() {
        //e(String name, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-dead-letter-exchange", "order-event-exchange");
        arguments.put("x-dead-letter-routing-key", "order.release.order");
        arguments.put("x-message-ttl", 1000 * 60 * 2);
        return new Queue("order.delay.queue", true, false, false, arguments);
    }

    @Bean
    public Queue orderReleaseOrderQueue() {
        return new Queue("order.release.order.queue", true, false, false);
    }

    @Bean
    public Exchange orderEventExchange() {
        return new TopicExchange("order-event-exchange", true, false);
    }

    @Bean
    public Binding orderCreatedOrderBinding() {
        return new Binding("order.delay.queue", Binding.DestinationType.QUEUE, "order-event-exchange",
                "order.create.order", null);
    }

    @Bean
    public Binding orderReleaseOrderBinding() {
        return new Binding("order.release.order.queue", Binding.DestinationType.QUEUE, "order-event-exchange",
                "order.release.order", null);
    }

    // 订单关闭直接和库存释放进行绑定
    @Bean
    public Binding orderReleaseOtherBinding() {
        return new Binding("stock.release.stock.queue", Binding.DestinationType.QUEUE, "order-event-exchange",
                "order.release.other.#", null);
    }

    @Bean
    public Queue orderSeckillOrderQueue() {
        return new Queue("order.seckill.order.queue", true, false, false);
    }

    @Bean
    public Binding orderSeckillOrderQueueBinding() {
        return new Binding("order.seckill.order.queue", Binding.DestinationType.QUEUE, "order-event-exchange",
                "order.seckill.order", null);
    }
}
