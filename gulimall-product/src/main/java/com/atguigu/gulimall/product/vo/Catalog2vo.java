package com.atguigu.gulimall.product.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class Catalog2vo {
    private String catalog1Id;
    private List<Catalog3vo> catalog3List;
    private String id;
    private String name;

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class Catalog3vo {
        private String catalog2Id;
        private String id;
        private String name;
    }
}
