package com.atguigu.gulimall.order.listener;

import com.atguigu.common.to.mq.SeckillOrderTo;
import com.atguigu.gulimall.order.entity.OrderEntity;
import com.atguigu.gulimall.order.service.OrderService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@RabbitListener(queues = "order.seckill.order.queue")
@Component
public class OrderSeckillListener {
    @Autowired
    OrderService orderService;

    @RabbitHandler
    public void closeOrder(SeckillOrderTo orderTo, Message message, Channel channel) throws IOException {
        log.warn("收到秒杀服务的订单消息: {}, rabbitMessage={}", orderTo, message);
        try {
            orderService.createSeckillOrder(orderTo);
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        } catch (Exception e) {
            log.error("处理关闭订单消息产生异常，e={}", e.getMessage(), e);
            channel.basicReject(message.getMessageProperties().getDeliveryTag(), true);
        }
    }
}
