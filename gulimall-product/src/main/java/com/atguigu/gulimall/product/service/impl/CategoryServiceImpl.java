package com.atguigu.gulimall.product.service.impl;

import com.atguigu.gulimall.product.service.CategoryBrandRelationService;
import com.atguigu.gulimall.product.vo.Catalog2vo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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


@Service("categoryService")
public class CategoryServiceImpl extends ServiceImpl<CategoryDao, CategoryEntity> implements CategoryService {

//    @Autowired
//    CategoryDao categoryDao;

    @Autowired
    CategoryBrandRelationService categoryBrandRelationService;

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
    @Transactional
    @Override
    public void updateCascade(CategoryEntity category) {
        this.updateById(category);
        categoryBrandRelationService.updateCategory(category.getCatId(),category.getName());
    }

    @Override
    public List<CategoryEntity> getLevel1Category() {
        List<CategoryEntity> list = baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("parent_cid", 0));
        return list;
    }

    @Override
    public Map<String, List<Catalog2vo>> getCatalogJson() {
        List<CategoryEntity> allList = baseMapper.selectList(null);

        //List<CategoryEntity> list1 = getLevel1Category();
        List<CategoryEntity> list1 = getParent_cid(allList, 0L);
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