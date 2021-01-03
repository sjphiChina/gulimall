package com.atguigu.common.to.mq;

import lombok.Data;

@Data
public class StockLockedTo {

    private Long id; //库存工作单的Id
    private StockDetailTo detail; //工作详情
}
