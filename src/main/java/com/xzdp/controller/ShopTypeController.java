package com.xzdp.controller;


import com.xzdp.dto.Result;
import com.xzdp.service.IShopTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    @GetMapping("list")
    public Result queryTypeList() {
        //店铺类型查询  插入缓存
        return typeService.queryList();

        //店铺类型 直接查询数据库
//        List<ShopType> typeList = typeService
//                .query().orderByAsc("sort").list();
//        return Result.ok(typeList);
    }
}
