package com.xzdp.service;

import com.xzdp.dto.Result;
import com.xzdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    /**
     * 根据id查询 商户信息
     * @param id 商户id
     * @return
     */
    Result queryById(Long id);

    /**
     * 更新商户信息
     * key 要保证 缓存和数据库的一致性
     *  先更新数据库，然后删除缓存
     *
     * @param shop 待更新的商户信息
     * @return
     */
    Result update(Shop shop);

    /**
     *  查询对应分类下的店铺信息
     *
     * @param typeId 店铺类型id
     * @param current 当前页
     * @param sortBy 排序方式
     * @param x 经度
     * @param y 维度
     * @return 店铺列表
     */
    Result queryShopByType(Integer typeId, Integer current, String sortBy, Double x, Double y);
}
