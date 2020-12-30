package com.atguigu.gulimall.order.service.impl;

import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.exception.BizCodeEnume;
import com.atguigu.common.exception.NoStockException;
import com.atguigu.common.exception.UnknownException;
import com.atguigu.common.utils.R;
import com.atguigu.common.utils.SnowFlake;
import com.atguigu.common.vo.MemberVo;
import com.atguigu.gulimall.order.constant.OrderConstant;
import com.atguigu.gulimall.order.dao.OrderItemDao;
import com.atguigu.gulimall.order.entity.OrderItemEntity;
import com.atguigu.gulimall.order.enume.OrderStatusEnum;
import com.atguigu.gulimall.order.feign.CartFeignService;
import com.atguigu.gulimall.order.feign.MemberFeignService;
import com.atguigu.gulimall.order.feign.ProductFeignService;
import com.atguigu.gulimall.order.feign.WareFeignService;
import com.atguigu.gulimall.order.interceptor.LoginUserInterceptor;
import com.atguigu.gulimall.order.service.OrderItemService;
import com.atguigu.gulimall.order.to.OrderCreateTo;
import com.atguigu.gulimall.order.vo.MemberAddressVo;
import com.atguigu.gulimall.order.vo.OrderConfirmVo;
import com.atguigu.gulimall.order.vo.OrderItemVo;
import com.atguigu.gulimall.order.vo.OrderSubmitVo;
import com.atguigu.gulimall.order.vo.SkuStockVo;
import com.atguigu.gulimall.order.vo.SpuInfoVo;
import com.atguigu.gulimall.order.vo.SubmitOrderResponseVo;
import com.atguigu.gulimall.order.vo.WareSkuLockVo;
import com.mysql.cj.x.protobuf.MysqlxCrud;
import lombok.ToString;
import org.aspectj.weaver.ast.Or;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.lang.reflect.Member;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.order.dao.OrderDao;
import com.atguigu.gulimall.order.entity.OrderEntity;
import com.atguigu.gulimall.order.service.OrderService;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;


@Service("orderService")
public class OrderServiceImpl extends ServiceImpl<OrderDao, OrderEntity> implements OrderService {

    // TIP 因为可以用ServiceImpl已经提供的save来保存entity，所以不用再引入dao
//    @Autowired
//    OrderDao orderDao;
//
//    @Autowired
//    OrderItemDao orderItemDao;
    @Autowired
    OrderItemService orderItemService;

    @Autowired
    MemberFeignService memberFeignService;

    @Autowired
    CartFeignService cartFeignService;

    @Autowired
    ThreadPoolExecutor threadPoolExecutor;

    @Autowired
    WareFeignService wareFeignService;

    @Autowired
    ProductFeignService productFeignService;

    @Autowired
    StringRedisTemplate redisTemplate;

    /**
     * datacenterId;  数据中心
     * machineId;     机器标识
     * 在分布式环境中可以从机器配置上读取
     * 单机开发环境中先写死
     */
    private SnowFlake snowFlake = new SnowFlake(1, 1);
    private ThreadLocal<OrderSubmitVo> orderSubmitVoThreadLocal = new ThreadLocal<>();

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<OrderEntity> page = this.page(
                new Query<OrderEntity>().getPage(params),
                new QueryWrapper<OrderEntity>()
        );

        return new PageUtils(page);
    }

    @Override
    public OrderConfirmVo confirmOrder() throws ExecutionException, InterruptedException {
        OrderConfirmVo confirmVo = new OrderConfirmVo();

        // TIP 解决feign的请求头丢失问题
        //此处需解决feign的请求头丢失问题，即远程调用时没有cookie
        //为此，添加了GuliFeignConfig，为feign添加了interceptor，参见GuliFeignConfig
        MemberVo memberVo = LoginUserInterceptor.loginUser.get();

        // TIP 解决feign的异步编排导致请求头丢失问题，如下
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();

        CompletableFuture<Void> getAddressFuture = CompletableFuture.runAsync(() -> {
            //将主线程的attribute传递给子线程
            RequestContextHolder.setRequestAttributes(attributes);
            //查询所有的收货地址的列表
            List<MemberAddressVo> address = memberFeignService.getAddress(memberVo.getId());
            confirmVo.setAddressVoList(address);
        }, threadPoolExecutor);

        CompletableFuture<Void> cartFuture = CompletableFuture.runAsync(() -> {
            RequestContextHolder.setRequestAttributes(attributes);
            //查询购物车中的所有选中项
            List<OrderItemVo> itemVos = cartFeignService.getCurrentUserCartItems();
            confirmVo.setItemVos(itemVos);

            //计算总价
            BigDecimal total = new BigDecimal("0");
            if (itemVos != null) {
                for (OrderItemVo itemVo : itemVos) {
                    BigDecimal multiply = itemVo.getPrice().multiply(new BigDecimal(itemVo.getCount().toString()));
                    total = total.add(multiply);
                }
            }
            confirmVo.setTotal(total);
            confirmVo.setPayPrice(total);
        }, threadPoolExecutor).thenRunAsync(() -> {
            // TIP 这里承接上一个CompletableFuture，可到所有sku的库存信息，然后映射成一个map，存入confirmVo
            List<OrderItemVo> itemVos = confirmVo.getItemVos();
            List<Long> collect = itemVos.stream().map(item -> item.getSkuId()).collect(Collectors.toList());
            R hasStock = wareFeignService.getSkusHasStock(collect);
            List<SkuStockVo> data = hasStock.getData(new TypeReference<List<SkuStockVo>>() {});
            if (data != null) {
                Map<Long, Boolean> map = data.stream()
                        .collect(Collectors.toMap(SkuStockVo::getSkuId, SkuStockVo::getHasStock));
                confirmVo.setStocks(map);
            }
        }, threadPoolExecutor);


        //查询用户积分
        Integer integration = memberVo.getIntegration();
        confirmVo.setIntegration(integration);

        // KNOW 防重复令牌 基于token机制，token存于redis和前端页面
        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue()
                .set(OrderConstant.USER_ORDER_TOKEN_PREFIX + memberVo.getId(), token, 30, TimeUnit.MINUTES);
        confirmVo.setOrderToken(token);

        CompletableFuture.allOf(getAddressFuture,cartFuture).get();



        return confirmVo;
    }

    // KNOW DB事务的4种隔离级别
    //@Transactional(isolation = Isolation.READ_COMMITTED)
    // KNOW DB事务的传播行为
//    @Transactional(propagation = Propagation.REQUIRED)
//    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Transactional
    @Override
    public SubmitOrderResponseVo submitOrder(OrderSubmitVo vo) {
        // TIP 用threadlocal将OrderSubmitVo共享
        orderSubmitVoThreadLocal.set(vo);
        SubmitOrderResponseVo responseVo = new SubmitOrderResponseVo();
        MemberVo memberVo = LoginUserInterceptor.loginUser.get();

        //1 KNOW 验证令牌 [令牌的对比和删除必须保证原子性]
        //  String voToken = vo.getOrderToken();
        //  String redisToken = redisTemplate.opsForValue().get(OrderConstant.USER_ORDER_TOKEN_PREFIX + memberVo.getId());
        //  if (voToken != null && !voToken.equals(redisToken))
        //     return null;

        // 这里使用redis的lua脚本，如果key不存在，返回0；如果key存在，对比成功返回1，失败返回0
        String luaScript = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        String voToken = vo.getOrderToken();
        //原子验证和删除令牌
        Long result = redisTemplate.execute(new DefaultRedisScript<Long>(luaScript, Long.class),
                Arrays.asList(OrderConstant.USER_ORDER_TOKEN_PREFIX + memberVo.getId()), voToken);
        if (result == 0l) {
            //令牌验证失败
            responseVo.setCode(1);
            return responseVo;
        }
        // 1 创建订单
        OrderCreateTo orderCreateTo = createOrder();
        // 2 验价
        BigDecimal payAmount = orderCreateTo.getOrder().getPayAmount();
        BigDecimal payPrice = vo.getPayPrice();
        // TODO 目前忽略对比验价
        if (Math.abs(payAmount.subtract(payAmount).doubleValue()) < 10) {
            //金额对比合理
            // 3 保存订单
            saveOrder(orderCreateTo);
            // 4 远程锁定库存,只要有异常就回滚
            // 订单号，所有订单项信息（skuid，skuname，num)
            WareSkuLockVo lockVo = new WareSkuLockVo();
            lockVo.setOrderSn(orderCreateTo.getOrder().getOrderSn());
            // TIP 利用stream.map产生更新后的同类型数据
            List<OrderItemVo> orderItemVos = orderCreateTo.getOrderItems().stream().map(item -> {
                OrderItemVo itemVo = new OrderItemVo();
                itemVo.setSkuId(item.getSkuId());
                itemVo.setCount(item.getSkuQuantity());
                itemVo.setTitle(item.getSkuName());
                return itemVo;
            }).collect(Collectors.toList());
            lockVo.setLocks(orderItemVos);
            R r = wareFeignService.orderLockStock(lockVo);
            if (r.getCode() == 0) {
                //库存锁定成功了
                responseVo.setCode(0);
                responseVo.setOrder(orderCreateTo.getOrder());
            } else {
                //为让order操作回滚，抛出异常
                //responseVo.setCode(3);
                if (r.getCode() == BizCodeEnume.NO_STOCK_EXCEPTION.getCode())
                    throw new NoStockException("商品库存不足");
                throw new UnknownException(r.getMsg());
            }
        } else {
            //金额对比失败
            responseVo.setCode(2);
        }
        return responseVo;
    }

    private void saveOrder(OrderCreateTo orderCreateTo) {
        OrderEntity orderEntity = orderCreateTo.getOrder();
        orderEntity.setModifyTime(new Date());
        //orderDao.insert(orderEntity);
        this.save(orderEntity);
        List<OrderItemEntity> orderItemEntities = orderCreateTo.getOrderItems();
        // KNOW 数据saveBatch批量保存
        orderItemService.saveBatch(orderItemEntities);
    }

    private OrderCreateTo createOrder() {
        OrderCreateTo orderCreateTo = new OrderCreateTo();
        // TIP 雪花算法生成订单号
        String orderId = String.valueOf(snowFlake.nextId());
        // 1. 生成订单
        OrderEntity orderEntity = buildOrder(orderId);
        // 2. 获得所有的订单项
        List<OrderItemEntity> itemEntities = buildOrderItems(orderId);

        // 3. TODO 验价 确定价格准确 这里就省略了细节 video279
        computePrice(orderEntity, itemEntities);
        orderCreateTo.setOrder(orderEntity);
        orderCreateTo.setOrderItems(itemEntities);
        orderCreateTo.setPayPrice(orderEntity.getPayAmount());
        orderCreateTo.setFare(new BigDecimal("0.0"));
        return orderCreateTo;
    }

    private void computePrice(OrderEntity orderEntity, List<OrderItemEntity> itemEntities) {
        BigDecimal totalPrice = new BigDecimal("0.0");
        for (OrderItemEntity item: itemEntities) {
            totalPrice = totalPrice.add(item.getRealAmount());
        }
        orderEntity.setTotalAmount(totalPrice);
        //应付总额
        orderEntity.setPayAmount(totalPrice.add(orderEntity.getFreightAmount()));
        //省略此处逻辑
        orderEntity.setPromotionAmount(new BigDecimal("0.0"));
    }

    private OrderEntity buildOrder(String orderSn) {
        MemberVo memberVo = LoginUserInterceptor.loginUser.get();
        OrderEntity entity = new OrderEntity();
        entity.setOrderSn(orderSn);
        entity.setMemberId(memberVo.getId());
        //1 获取收货信息
        OrderSubmitVo submitVo = orderSubmitVoThreadLocal.get();
        //2 获取运费，为简化，此处固定运费
        entity.setFreightAmount(new BigDecimal("100"));
        // TODO 设置收货人信息
        //3 视频中由于要基于收货人地址由ware模块动态计算运费，收货人地址是由ware提供的，
        //这里我们就直接从member模块获得收货人地址
        R r = memberFeignService.getShippingAddress(submitVo.getAddrId());
        MemberAddressVo addressVo = r.getData("memberReceiveAddress", new TypeReference<MemberAddressVo>(){});
        entity.setReceiverCity(addressVo.getCity());
        entity.setReceiverDetailAddress(addressVo.getDetailAddress());
        entity.setReceiverName(addressVo.getName());
        entity.setReceiverPhone(addressVo.getPhone());
        entity.setReceiverPostCode(addressVo.getPostCode());
        entity.setReceiverProvince(addressVo.getProvince());
        entity.setReceiverRegion(addressVo.getRegion());
        //4 设置订单的状态
        entity.setStatus(OrderStatusEnum.CREATE_NEW.getCode());
        //自动确认时间
        entity.setAutoConfirmDay(7);
        return entity;
    }

    //构建所有订单项
    private List<OrderItemEntity> buildOrderItems(String orderSn) {
        //最后一次确定每个购物项的价格
        List<OrderItemVo> currentUserCartItems = cartFeignService.getCurrentUserCartItems();
        if (currentUserCartItems != null && currentUserCartItems.size() > 0) {
            List<OrderItemEntity> itemEntities = currentUserCartItems.stream().map(cartItem -> {
                OrderItemEntity itemEntity = buildOrderItem(cartItem);
                itemEntity.setOrderSn(orderSn);
                return itemEntity;
            }).collect(Collectors.toList());
            return itemEntities;
        }
        return null;
    }

    private OrderItemEntity buildOrderItem(OrderItemVo cartItem) {
        OrderItemEntity itemEntity = new OrderItemEntity();
        // 1. 订单信息:订单号
        itemEntity.setSkuId(cartItem.getSkuId());
        // 2. spu信息
        Long skuId = itemEntity.getSkuId();
        R r = productFeignService.getSpuInfoBySkuId(skuId);
        SpuInfoVo data = r.getData(new TypeReference<SpuInfoVo>() {});
        itemEntity.setSpuId(data.getId());
        itemEntity.setSpuBrand(data.getBrandId().toString());
        itemEntity.setSpuName(data.getSpuName());
        itemEntity.setCategoryId(data.getCatalogId());

        // 3. sku信息
        itemEntity.setSkuName(cartItem.getTitle());
        itemEntity.setSkuPic(cartItem.getImage());
        itemEntity.setSkuPrice(cartItem.getPrice());
        String skuAttr = StringUtils.collectionToDelimitedString(cartItem.getSkuAttr(), ";");
        itemEntity.setSkuAttrsVals(skuAttr);
        itemEntity.setSkuQuantity(cartItem.getCount());
        // 4. 优惠信息，暂时不做
        itemEntity.setGiftGrowth(0);
        itemEntity.setGiftIntegration(0);

        // 5. 积分信息
        itemEntity.setGiftGrowth(cartItem.getPrice().intValue());
        itemEntity.setGiftIntegration(cartItem.getCount().intValue());

        BigDecimal orign = itemEntity.getSkuPrice().multiply(new BigDecimal(itemEntity.getSkuQuantity().toString()));
        //此处省略各种优惠促销减免
        itemEntity.setRealAmount(orign);

        return itemEntity;
    }
}