package com.atguigu.gulimall.ware;

import com.atguigu.gulimall.ware.dao.WareSkuDao;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
public class GulimallWareApplicationTests {

    @Autowired
    WareSkuDao dao;

    @Test
    public void contextLoads() {
        long res = dao.lockSkuStock(3l, 1l, 1);
        System.out.println("=========test:" + res);
    }

}
