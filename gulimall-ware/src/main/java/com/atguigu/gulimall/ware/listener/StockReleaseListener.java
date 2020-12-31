package com.atguigu.gulimall.ware.listener;

import com.atguigu.common.to.mq.OrderTo;
import com.atguigu.common.to.mq.StockLockedTo;
import com.atguigu.gulimall.ware.service.WareSkuService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@Slf4j
@RabbitListener(queues = "stock.release.stock.queue")
public class StockReleaseListener {

    @Autowired
    WareSkuService wareSkuService;

    //如果解锁失败，一定要告诉mq解锁失败，消息不能删掉
    @RabbitHandler
    public void handleStockLockedRelease(StockLockedTo to, Message message, Channel channel) throws IOException {
        log.warn("收到库存解锁的消息: {},\n rabbitMessage={}", to, message);
        try {
            wareSkuService.unlockStock(to);
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            log.error("库存解锁失败:{}", e.getMessage(), e);
            channel.basicReject(message.getMessageProperties().getDeliveryTag(), true);
        }
    }

    //处理OrderService.closeOrder()发出的消息
    //rabbitTemplate.convertAndSend("order-event-exchange", "order.release.other", entityNew);
    @RabbitHandler
    public void handleOrderClose(OrderTo to, Message message, Channel channel) throws IOException {
        log.warn("收到订单关闭的消息，需要解锁库存: {},\n rabbitMessage={}", to, message);
        try {
            wareSkuService.unlockStock(to);
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            log.error("库存解锁失败:{}", e.getMessage(), e);
            channel.basicReject(message.getMessageProperties().getDeliveryTag(), true);
        }
    }
}
