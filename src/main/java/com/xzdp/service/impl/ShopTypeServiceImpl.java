package com.xzdp.service.impl;

import com.xzdp.dto.Result;
import com.xzdp.entity.ShopType;
import com.xzdp.mapper.ShopTypeMapper;
import com.xzdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xzdp.utils.TypeCon;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.xzdp.utils.Constants.RedisConstants.CACHE_SHOPTYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    /**
     * 查询店铺类型
     * 先查询缓存，在查询数据库
     *  数据类型 使用  List
     * @return
     */
    @Override
    public Result queryList() {

        List<ShopType> typeList;
        String key = CACHE_SHOPTYPE_KEY;

        //1.向缓存中查询店铺类型
        List<String> typeListJson = stringRedisTemplate.opsForList().range(key, 0, -1);
//        System.out.println(typeListJson);

        //2. 缓存命中，返回店铺类型信息
//        assert typeListJson != null; 断言不为null
        // 如果缓存中没有数据 会返回一个空的list集合 [] 不会返回null
        if (!typeListJson.isEmpty()) {
            log.info("缓存命中!");
            typeList = TypeCon.listToBean(typeListJson, ShopType.class);
//            System.out.println(typeList);
            return Result.ok(typeList);
        }

        //3. 缓存未命中，查询数据库
        typeList= list();
        log.info("缓存未命中，查询数据库！");
//        System.out.println(typeList);


        //4. 将店铺类型信息存到缓存
        // 按照sorted进行排序，小的在前，大的在后
        stringRedisTemplate.opsForList().rightPushAll(key, TypeCon.beanToListBySort(typeList));

        //5. 返回数据给前端
        return Result.ok(typeList);
    }
}
