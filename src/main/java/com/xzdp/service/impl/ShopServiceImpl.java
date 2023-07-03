package com.xzdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzdp.dto.Result;
import com.xzdp.entity.Shop;
import com.xzdp.mapper.ShopMapper;
import com.xzdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xzdp.utils.Redis.CacheClient;
import com.xzdp.dto.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.xzdp.utils.Constants.RedisConstants.*;
import static com.xzdp.utils.Constants.SystemConstants.DEFAULT_PAGE_SIZE;

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
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient client;

    /**
     *  根据id查询 商户信息
     *   Redis 数据类型选择：Value使用String数据类型
     *
     *  1. 先从redis中查询数据
     *  2. 缓存命中：
     *      2.1 缓存数据不为空，直接返回店铺数据
     *      2.2 缓存数据为空，则返回错误id
     *   3. 缓存未命中：
     *      3.1 获取互斥锁
     *      3.2 获取锁失败，休眠等待一段时间，然后再从Redis中查询数据
     *      3.3 获取锁成功，查询数据库
     *   4. 数据库查询：
     *      4.1 查询失败,向Redis中写入空字符串，并设置较短的有效期，避免缓存穿透。
     *      4.2 查询成功,将数据写入Redis缓存
     *   5. 释放互斥锁
     *   6. 给前端返回数据
     *
     * @param id  商户id
     * @return 返回商户信息给前端
     */
    @Override
    public Result queryById(Long id) {
        //1. 自定义方法

        //1.1 缓存穿透
//        return queryWithPassThrough(id);

        //1.2 缓存穿透 + 互斥锁解决缓存击穿
//        return queryWithMutex(id);

        //1.3 逻辑过期解决缓存击穿
//        return queryWithLogicalExpire(id);

        //2. 使用工具类封装好的方法
        //2.1 缓存穿透
//        Shop shop = client.
//                queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //2.3 逻辑过期解决缓存穿透
        Shop shop = client.
                queryWithLogicalExpire(CACHE_SHOP_LOGIC_KEY, LOCK_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);


        if (shop == null) {
            return Result.fail("店铺信息不存在！");
        }
        return Result.ok(shop);
    }



    /**
     *  避免缓存穿透
     *
     *  1. 先从redis中查询数据
     *  2. 缓存命中：
     *      2.1 缓存数据不为空，直接返回店铺数据
     *      2.2 缓存数据为空，则返回错误id
     *  3. 缓存未命中，查询数据库
     *  4. 查询失败,向Redis中写入空字符串，并设置较短的有效期（2M），避免缓存穿透。
     *  5. 查询成功，写入redis,并设置超时时间，（30M)3
     *  6. 给前端返回数据
     *
     * @param id 店铺id
     * @return 返回封装结果
     */
    private Result queryWithPassThrough(Long id) {
        String shopKey = CACHE_SHOP_KEY + id;
        //1. 从redis中查询店铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        //当shopKey不存在时，shopJson为null-- 缓存未命中
        //当id为虚假id，redis中存入空数据时，shopJson为"" (空字符串) -- 缓存的虚假命中
//        System.out.println(shopJson);

        //2. 缓存命中，分两种情况处理
        //2.1 缓存结果不为空，直接返回
        //如果shopJson是空字符串，isBlank返回的是false
        if (StrUtil.isNotBlank(shopJson)) {
            log.info("缓存命中！");
            Shop shop = JSON.parseObject(shopJson, Shop.class);
            return Result.ok(shop);
        }
        //2.2 缓存结果为空
        if ("".equals(shopJson)) {
            log.info("虚假id");
            return Result.fail("你这id是假的吧？？");
        }

        //3. 查询数据库
        Shop shop = getById(id);
        //4. 商户不存在，向redis中存入null值，避免缓存穿透
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("商户信息不存在！");
        }
        //5. 商户存在，写入redis,并设置超时时间，30分钟
        shopJson = JSON.toJSONString(shop);
        stringRedisTemplate.opsForValue().set(shopKey, shopJson, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        log.info("将数据库数据写入缓存");
        //6. 将信息返回给前端
        return Result.ok(shop);
    }



    /**
     *  缓存穿透+
     *  互斥锁解决缓存击穿
     *
     *  1. 先从redis中查询数据
     *  2. 缓存命中：
     *      2.1 缓存数据不为空，直接返回店铺数据
     *      2.2 缓存数据为空，则返回错误id
     *   3. 缓存未命中：
     *      3.1 获取互斥锁
     *      3.2 获取锁失败，休眠等待一段时间，然后再从Redis中查询数据
     *      3.3 获取锁成功，查询数据库
     *   4. 数据库查询：
     *      4.1 查询失败,向Redis中写入空字符串，并设置较短的有效期，避免缓存穿透。
     *      4.2 查询成功,将数据写入Redis缓存
     *   5. 释放互斥锁
     *   6. 给前端返回数据
     * @param id 店铺id
     * @return 返回封装结果
     */
    private Result queryWithMutex(Long id) {
        String shopKey = CACHE_SHOP_KEY + id;
        //1. 从redis中查询店铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        //当shopKey不存在时，shopJson为null-- 缓存未命中
        //当id为虚假id，redis中存入空数据时，shopJson为"" (空字符串) -- 缓存的虚假命中
//        System.out.println(shopJson);

        //2. 缓存命中，分两种情况处理
        //2.1 缓存结果不为空，直接返回
        //如果shopJson是空字符串，isBlank返回的是false
        if (StrUtil.isNotBlank(shopJson)) {
            log.info("缓存命中！");
            Shop shop = JSON.parseObject(shopJson, Shop.class);
            return Result.ok(shop);
        }
        //2.2 缓存结果为空
        if ("".equals(shopJson)) {
            log.info("虚假id");
            return Result.fail("你这id是假的吧？？");
        }

        //3. 缓存未命中,实现缓存重建
        //3.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = tryGetLock(lockKey, LOCK_SHOP_TTL);
            if (!isLock) {
                //3.2 获取锁失败，休眠等待一段时间，然后再从Redis中查询数据
                Thread.sleep(50);
                //使用递归的方式 来重试获取数据
                return queryWithMutex(id);
            }
            //3.3 获取锁成功，查询数据库
            log.info("获取锁成功");
            shop = getById(id);
            // 模拟重建的延时
            Thread.sleep(200);

            //4. 数据库查询
            //4.1 查询失败，向redis中存入null值，设置较短的有效期，避免缓存穿透
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return Result.fail("商户信息不存在！");
            }
            //4.2 查询成功，写入redis,并设置超时时间，30分钟
            shopJson = JSON.toJSONString(shop);
            stringRedisTemplate.opsForValue().set(shopKey, shopJson, CACHE_SHOP_TTL, TimeUnit.MINUTES);
            log.info("将数据库数据写入缓存");
        } catch (InterruptedException e) {
            throw new RuntimeException();
        } finally {
            //5. 释放互斥锁
            unLock(lockKey);
        }


        //6. 将信息返回给前端
        return Result.ok(shop);
    }


    //创建一个线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     *  不考虑缓存穿透问题
     *  逻辑过期解决缓存击穿
     *
     *  1. 从redis中查询店铺缓存
     *  2. 缓存未命中，返回错误
     *  3. 缓存命中，获取判断缓存是否过期
     *     3.1 缓存未过期，返回店铺信息
     *     3.2 缓存过期，尝试获取互斥锁
     *  4. 互斥锁获取
     *     4.1 成功，开启独立线程--5
     *     4.2 成功+失败，返回-旧的-信息
     *  5. 独立线程
     *     5.1 查询数据库
     *     5.2 数据写入Redis
     *     5.3 释放互斥锁
     *
     *
     * @param id 店铺id
     * @return 返回封装结果
     */
    private Result queryWithLogicalExpire(Long id) {
        String shopKey = CACHE_SHOP_LOGIC_KEY + id;
        //1. 从redis中查询店铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);

        //2. 缓存未命中，返回错误
        if (StrUtil.isBlank(shopJson)) {
            log.info("缓存未命中！");
            return Result.fail("店铺信息不存在！");
        }

        //3 缓存命中，判断缓存是否过期
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        //获取存活时间
        LocalDateTime expireTime = redisData.getExpireTime();
        //获取商户信息
        // tool 利用工具类 转换 Object类型
        Shop data = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        //3.1 缓存未过期，返回店铺信息
        // key 时间操作 API
        // isAfter 存活时间在当前时间之后，说明没有过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            log.info("缓存没有过期！");
            return Result.ok(data);
        }
        //3.2 缓存过期，尝试获取互斥锁
        log.info("缓存过期！");
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryGetLock(lockKey, LOCK_SHOP_TTL);


        //4. 互斥锁获取
        //4.1 key 成功，开启独立线程
        if (isLock) {
            log.info("获取锁成功！");
            //5. 独立线程
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                log.info("开启独立线程");
                //重建缓存,是否锁
                try {
                    //5.1 查询数据库
                    //5.2 数据写入Redis
                    saveShop2Redis(id, 30L);
                    log.info("更新缓存信息成功！");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //5.3 释放互斥锁
                    unLock(lockKey);
                }
            });
        }
        //4.2 成功+失败，返回-旧的-信息
        return Result.ok(data);

    }




    /**
     *  更新店铺信息
     *  先更新数据库，然后删除缓存
     *
     *  开始事务 保证一致性
     *
     * @param shop 待更新的商户信息
     * @return 返回商户信息给前端
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("商户信息不存在！");
        }
        //1. 更新数据库
        updateById(shop);

        //2. 删除缓存
        String key = CACHE_SHOP_KEY + shop.getId();
        stringRedisTemplate.delete(key);
        log.info("商户信息缓存已删除");

        return Result.ok();
    }


    /**
     *  根据商铺类型分页查询商铺信息
     *
     *  现在先默认按照距离查询，按照人气和评分先不实现
     *
     * @param typeId 店铺类型id
     * @param current 当前页
     * @param sortBy 排序方式 默认按距离，其他方法先不实现h
     * @param x 经度 -- 假的
     * @param y 维度 -- 假的
     * @return 店铺列表
     */
    @Override
    public Result queryShopByType(Integer typeId, Integer current, String sortBy, Double x, Double y) {
        log.info("x:{},y:{}", x, y);

        //1. 判断 是否需要根据距离排序
        if (x == null || y == null) {
            // 不需要根据距离排序，直接返回 分页查询结果
            Page<Shop> page = lambdaQuery()
                    .eq(Shop::getTypeId, typeId)
                    .page(new Page<>(current, DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        //2. 计算分页参数
        int begin = (current - 1) * DEFAULT_PAGE_SIZE;
        int end = begin + DEFAULT_PAGE_SIZE;

        //3. 查询Redis，按照距离排序，分页。 结果：shopId，distance
        String key = SHOP_GEO_KEY + typeId;
        // 10公里 -- 这里应该是由前端来传
        double dis = 10000.0;
        // geobysearch bylonlat x y byradius 10 withdistance
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(key,
                        GeoReference.fromCoordinate(x, y),
                        // 指定半径，默认单位为米
                        new Distance(dis),
                        // 指定返回结果带上距离值
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance()
                                // 分页限制，只能从0开始查，这里传递的参数是末尾
                                .limit(end)
                );

        //4. 解析出id
        if (results == null) {
            return Result.ok("还没有店铺入住哦！");
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> contentList = results.getContent();
        // 当集合中的内容 小于begin时就直接返回即可
        if (contentList.size() <= begin) {
            //没有下一页了
            return Result.ok("没有店铺咯！");
        }
        // 4.1 截取 begin 到 end的部分
        // 当知道集合大小时，创建的时候带上大小，提高效率
        List<Long> shopIdList = new ArrayList<>(contentList.size());
        Map<Long, Double> distanceMap = new HashMap<>(contentList.size());
        // skip 跳过 begin前面的部分，从begin开始
        contentList.stream().skip(begin).forEach(result -> {
            // 店铺id
            Long shopId = Long.valueOf(result.getContent().getName());
            shopIdList.add(shopId);
            distanceMap.put(shopId, result.getDistance().getValue());
        });



        //5. 根据id查询店铺，并添加距离信息
        // select * from tb_shop where id in (?, ?) order by field (id, ? , ?)
        String idStr = StrUtil.join(",", shopIdList);
        List<Shop> shops = lambdaQuery().in(Shop::getId, shopIdList)
                .last("order by field (id," + idStr + ")").list();
        //添加距离信息
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId()));
        }


        return Result.ok(shops);
    }


    /**
     * 获取互斥锁
     * @param redisKey 加锁的键值
     * @param timeOut ttl有效期
     * @return 成功返回true，失败返回false
     */
    public boolean tryGetLock(String redisKey, Long timeOut) {
        // 通过 setnx 设置 锁 lock, 设置过期时间为10秒钟
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(redisKey, "1", timeOut, TimeUnit.SECONDS);
        // tool 利用工具类自动 拆箱
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 删除互斥锁
     * @param redisKey 删除锁的键值
     */
    public void unLock(String redisKey) {
        stringRedisTemplate.delete(redisKey);
    }


    /**
     *  将数据库信息封装，存到Redis中
     * @param id 店铺id
     * @param expireMinutes 逻辑过期时间 分钟
     */
    public void saveShop2Redis(Long id, Long expireMinutes) throws InterruptedException {
        //查数据库
        Shop shop = getById(id);
        //模拟缓存延时
        Thread.sleep(200);

        //封装缓存值
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        // 设置逻辑过期时间，当前时间-分钟
        redisData.setExpireTime(LocalDateTime.now().plusMinutes(expireMinutes)); //30M
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireMinutes)); //30s

        //存入缓存
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_LOGIC_KEY + shop.getId(), JSONUtil.toJsonStr(redisData));
    }

}
