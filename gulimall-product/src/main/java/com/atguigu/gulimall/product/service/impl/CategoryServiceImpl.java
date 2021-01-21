package com.atguigu.gulimall.product.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.gulimall.product.service.CategoryBrandRelationService;
import com.atguigu.gulimall.product.vo.Catalog2vo;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.product.dao.CategoryDao;
import com.atguigu.gulimall.product.entity.CategoryEntity;
import com.atguigu.gulimall.product.service.CategoryService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;


@Slf4j
@Service("categoryService")
public class CategoryServiceImpl extends ServiceImpl<CategoryDao, CategoryEntity> implements CategoryService {

//    @Autowired
//    CategoryDao categoryDao;

    @Autowired
    CategoryBrandRelationService categoryBrandRelationService;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    RedissonClient redissonClient;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<CategoryEntity> page = this.page(
                new Query<CategoryEntity>().getPage(params),
                new QueryWrapper<CategoryEntity>()
        );

        return new PageUtils(page);
    }

    @Override
    public List<CategoryEntity> listWithTree() {
        //1、查出所有分类
        List<CategoryEntity> entities = baseMapper.selectList(null);

        //2、组装成父子的树形结构

        //2.1）、找到所有的一级分类
        List<CategoryEntity> level1Menus = entities.stream().filter(categoryEntity ->
             categoryEntity.getParentCid() == 0
        ).map((menu)->{
            menu.setChildren(getChildrens(menu,entities));
            return menu;
        }).sorted((menu1,menu2)->{
            return (menu1.getSort()==null?0:menu1.getSort()) - (menu2.getSort()==null?0:menu2.getSort());
        }).collect(Collectors.toList());
        return level1Menus;
    }

    @Override
    public void removeMenuByIds(List<Long> asList) {
        //TODO  1、检查当前删除的菜单，是否被别的地方引用

        //逻辑删除
        baseMapper.deleteBatchIds(asList);
    }

    //[2,25,225]
    @Override
    public Long[] findCatelogPath(Long catelogId) {
        List<Long> paths = new ArrayList<>();
        List<Long> parentPath = findParentPath(catelogId, paths);

        Collections.reverse(parentPath);

        return parentPath.toArray(new Long[parentPath.size()]);
    }

    /**
     * 级联更新所有关联的数据
     * @param category
     */
    //@CacheEvict(value = "category", key = "'getLevel1Category'"),删除一种缓存
    //@CacheEvict(value = "category", allEntries = true)//等同于下面的操作，即删除category这个缓存分区里的所有数据
    @Caching( evict = {
            @CacheEvict(value = "category", key = "'getLevel1Category'"),
            @CacheEvict(value = "category", key = "'getCatalogJson'")
    })
    @Transactional
    @Override
    public void updateCascade(CategoryEntity category) {
        this.updateById(category);
        categoryBrandRelationService.updateCategory(category.getCatId(),category.getName());
    }

    // KNOW 3种cache的用法

    //每一个需要缓存的数据我们都要来指定要放入哪个名字的缓存，缓存的分区（可按照业务类型分类）
    //@Cacheable(value = {"category"}, key = "'level1Category'") //代表当前方法的需要缓存，如果缓存中有，方法不用调用，如果缓存中没有，会调用方法，最后将数据写入缓存
    @Cacheable(value = {"category"}, key = "#root.method.name")//用当前方法名作为key
    @Override
    public List<CategoryEntity> getLevel1Category() {
        List<CategoryEntity> list = baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("parent_cid", 0));
        return list;
    }

    //@Cacheable(value = "category", key = "#root.method.name")//2 使用Spring Cache模式
    @Override
    public Map<String, List<Catalog2vo>> getCatalogJson() {
        //1 使用普通模式
        String catalogJSON = redisTemplate.opsForValue().get("catalogJSON");
        if (StringUtils.isEmpty(catalogJSON)) {
            log.warn("=========================Redisson 不命中，需要访问getCatalogJsonFromDb，线程id: {}", Thread.currentThread().toString());
            Map<String, List<Catalog2vo>> catalogJsonFromDb = getCatalogJsonFromDbWithRedissonLock();
//            log.warn("=========================Redis 不命中，需要访问getCatalogJsonFromDb，线程id: {}", Thread.currentThread().toString());
//            Map<String, List<Catalog2vo>> catalogJsonFromDb = getCatalogJsonFromDbWithRedisLock();
//            log.warn("=========================lcoal cache 不命中，需要访问getCatalogJsonFromDb，线程id: {}", Thread.currentThread().toString());
//            Map<String, List<Catalog2vo>> catalogJsonFromDb = getCatalogJsonFromDbWithLocalLock();
            return catalogJsonFromDb;
        }
        Map<String, List<Catalog2vo>> result = JSON.parseObject(catalogJSON, new TypeReference<Map<String, List<Catalog2vo>>>(){});
        return result;

        //2 使用Spring Cache模式
        //return getCatalogJsonFromDbWithSpringCache();
    }

    // 直接使用Srping Cache
    public Map<String, List<Catalog2vo>> getCatalogJsonFromDbWithSpringCache() {
        log.warn("=========================spring cache 不命中，需要访问数据库，线程id: {}", Thread.currentThread().toString());
        List<CategoryEntity> allList = baseMapper.selectList(null);
        List<CategoryEntity> list1 = getParent_cid(allList, 0L);
        //封装数据
        Map<String, List<Catalog2vo>> parent_cid = list1.stream().collect(Collectors.toMap(k -> {
            return k.getCatId().toString();
        }, v -> {
            //每一个的一级分类,查到这个一级分类的二级分类
            List<CategoryEntity> categoryEntities = getParent_cid(allList, v.getCatId());
            List<Catalog2vo> catalog2vos = null;
            if (categoryEntities != null) {
                catalog2vos = categoryEntities.stream().map(l2 -> {

                    Catalog2vo catalog2vo = new Catalog2vo(v.getCatId().toString(), null, l2.getCatId().toString(),
                            l2.getName());
                    //找当前二级分类的3级分类封装成vo
                    List<CategoryEntity> level3Catalog = getParent_cid(allList, l2.getCatId());
                    if (level3Catalog != null) {
                        List<Catalog2vo.Catalog3vo> collect = level3Catalog.stream().map(l3 -> {
                            // 封装3级vo
                            Catalog2vo.Catalog3vo catalog3vo = new Catalog2vo.Catalog3vo(l2.getCatId().toString(),
                                    l3.getCatId().toString(), l3.getName());
                            return catalog3vo;
                        }).collect(Collectors.toList());
                        catalog2vo.setCatalog3List(collect);
                    }
                    return catalog2vo;
                }).collect(Collectors.toList());
            }
            return catalog2vos;
        }));
        return parent_cid;
    }


    // 使用Redisson分布式锁
    private Map<String, List<Catalog2vo>> getCatalogJsonFromDbWithRedissonLock() {
        // 1 锁的名字，即锁的粒度，越细越快
        RLock lock = redissonClient.getLock("catalog-lock");
        lock.lock();
        Map<String, List<Catalog2vo>> dataFromDb;
        try {
            dataFromDb = getCatalogJsonFromDB();
        } finally {
            lock.unlock();
        }
        return dataFromDb;
    }

    // 使用自己写的Redis分布式锁
    private Map<String, List<Catalog2vo>> getCatalogJsonFromDbWithRedisLock() {
        //要再次判断，此时已存在
        //加redis锁，加锁和设置过期时间必须是同步原子的，这样不用担心加锁到设置失效时间中间发生闪断的问题
        String uuid = UUID.randomUUID().toString();
        //相当于300s失效，简化了续期问题
        Boolean lock = redisTemplate.opsForValue().setIfAbsent("catalog-lock", uuid, 300, TimeUnit.SECONDS);
        if (lock) {
            //加锁成功...
            log.info("获取redis分布式锁成功，线程执行 {}", Thread.currentThread().toString());
            Map<String, List<Catalog2vo>> dataFromDb;
            try {
                dataFromDb = getCatalogJsonFromDB();
            } finally {
                String luaScript = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
                Long lock1 = redisTemplate.execute(new DefaultRedisScript<Long>(luaScript, Long.class), Arrays.asList("catalog-lock"), uuid);
            }
//            String lockValue = redisTemplate.opsForValue().get("redisLock");
//            if (uuid.equals(lockValue)) {
//                //保证要删掉自己的锁
//                //但其实仍有风险，因为redisTemplate.opsForValue().get("redisLock");和redisTemplate.delete("redisLock");是两步操作
//                //存在风险，需要改为原子操作，可用lua脚本
//                redisTemplate.delete("redisLock");
//            }

            return dataFromDb;
        } else {
            //加锁失败，
            // 1: 重试,自旋的方式
            log.info("没有获取到redis分布式锁，线程等待 {}", Thread.currentThread().toString());
            try {
                Thread.sleep(1000l);
            } catch (InterruptedException e) {
                log.error("等待redis分布式锁出错，线程 {}", Thread.currentThread().toString());
            }
            return getCatalogJsonFromDbWithRedisLock();
        }
    }

    // 使用本地锁
    private Map<String, List<Catalog2vo>> getCatalogJsonFromDbWithLocalLock() {
        synchronized (this) {
            return getCatalogJsonFromDB();
        }
    }

    private Map<String, List<Catalog2vo>> getCatalogJsonFromDB() {
        String catalogJSON = redisTemplate.opsForValue().get("catalogJSON");
        //要再次判断，是否此时已存在
        if (!StringUtils.isEmpty(catalogJSON)) {
            log.warn("=========================cache命中了，不用再访问db，线程id: {}", Thread.currentThread().toString());
            Map<String, List<Catalog2vo>> result = JSON
                    .parseObject(catalogJSON, new TypeReference<Map<String, List<Catalog2vo>>>() {});
            return result;
        }
        log.warn("=========================cache 不命中，需要访问数据库，线程id: {}", Thread.currentThread().toString());
        List<CategoryEntity> allList = baseMapper.selectList(null);
        List<CategoryEntity> list1 = getParent_cid(allList, 0L);
        //封装数据
        Map<String, List<Catalog2vo>> parent_cid = list1.stream().collect(Collectors.toMap(k -> {
            return k.getCatId().toString();
        }, v -> {
            //每一个的一级分类,查到这个一级分类的二级分类
            List<CategoryEntity> categoryEntities = getParent_cid(allList, v.getCatId());
            List<Catalog2vo> catalog2vos = null;
            if (categoryEntities != null) {
                catalog2vos = categoryEntities.stream().map(l2 -> {

                    Catalog2vo catalog2vo = new Catalog2vo(v.getCatId().toString(), null, l2.getCatId().toString(),
                            l2.getName());
                    //找当前二级分类的3级分类封装成vo
                    List<CategoryEntity> level3Catalog = getParent_cid(allList, l2.getCatId());
                    if (level3Catalog != null) {
                        List<Catalog2vo.Catalog3vo> collect = level3Catalog.stream().map(l3 -> {
                            // 封装3级vo
                            Catalog2vo.Catalog3vo catalog3vo = new Catalog2vo.Catalog3vo(l2.getCatId().toString(),
                                    l3.getCatId().toString(), l3.getName());
                            return catalog3vo;
                        }).collect(Collectors.toList());
                        catalog2vo.setCatalog3List(collect);
                    }
                    return catalog2vo;
                }).collect(Collectors.toList());
            }
            return catalog2vos;
        }));

        String s = JSON.toJSONString(parent_cid);
        redisTemplate.opsForValue().set("catalogJSON", s);
        return parent_cid;
    }

    //    private List<CategoryEntity> getParent_cid(CategoryEntity categoryEntity) {
//        return baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("parent_cid", categoryEntity.getCatId()));
//    }

    private List<CategoryEntity> getParent_cid(List<CategoryEntity> list, Long parent_cid) {
        //return baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("parent_cid", categoryEntity.getCatId()));
//        return list.stream().filter(item -> {
//            return item.getParentCid() == parent_cid;
//        }).collect(Collectors.toList());
        return list.stream().filter(item -> item.getParentCid() == parent_cid).collect(Collectors.toList());
    }

    //225,25,2
    private List<Long> findParentPath(Long catelogId,List<Long> paths){
        //1、收集当前节点id
        paths.add(catelogId);
        CategoryEntity byId = this.getById(catelogId);
        if(byId.getParentCid()!=0){
            findParentPath(byId.getParentCid(),paths);
        }
        return paths;

    }


    //递归查找所有菜单的子菜单
    private List<CategoryEntity> getChildrens(CategoryEntity root,List<CategoryEntity> all){

        List<CategoryEntity> children = all.stream().filter(categoryEntity -> {
            return categoryEntity.getParentCid() == root.getCatId();
        }).map(categoryEntity -> {
            //1、找到子菜单
            categoryEntity.setChildren(getChildrens(categoryEntity,all));
            return categoryEntity;
        }).sorted((menu1,menu2)->{
            //2、菜单的排序
            return (menu1.getSort()==null?0:menu1.getSort()) - (menu2.getSort()==null?0:menu2.getSort());
        }).collect(Collectors.toList());

        return children;
    }



}