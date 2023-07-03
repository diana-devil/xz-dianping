package com.xzdp.service;

import com.xzdp.dto.Result;
import com.xzdp.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopTypeService extends IService<ShopType> {


    /**
     * 查询店铺类型
     * 先查询缓存，在查询数据库
     * @return
     */
    Result queryList();
}
