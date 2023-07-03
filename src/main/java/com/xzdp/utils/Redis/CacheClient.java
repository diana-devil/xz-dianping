package com.xzdp.utils.Redis;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.xzdp.dto.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.xzdp.utils.Constants.RedisConstants.*;


/**
 * tool 封装Redis的工具类
 * */
@Slf4j
@Component
public class CacheClient {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间
     * @param setKey 键值
     * @param object 存入对象
     * @param time TTL 有效期
     * @param timeUnit 时间单位
     */
    public void set(String setKey, Object object, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(setKey, JSONUtil.toJsonStr(object), time, timeUnit);
    }


    /**
     * 将任意Java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓存击穿问题
     *
     * @param setKey 键值
     * @param object 存入对象
     * @param time 逻辑过期时间
     * @param timeUnit 时间单位
     */
    public void setLogic(String setKey, Object object, Long time, TimeUnit timeUnit) {
        //封装RedisData
        RedisData redisData = new RedisData();
        redisData.setData(object);
        // key 时间操作API
        redisData.setExpireTime(LocalDateTime.now().plusMinutes(timeUnit.toMinutes(time)));
        //存入Redis
        stringRedisTemplate.opsForValue().set(setKey, JSONUtil.toJsonStr(redisData));
    }



    /**
     *   用缓存null值的方式解决缓存穿透问题
     *
     *   单独的T代表一个类型，而Class<T>和Class<?>代表这个类型所对应的类
     *   方法定义：<ID, R>---fun(ID id, Class<R> type)
     *   方法调用： fun(id, Shop.class)     前面传的就是一个 Long类型id， 后面传的是Shop的Class对象
     *   其中，Long id   Shop shop   传入参数后，泛型ID推断为Long类型，泛型R推断为Shop类型
     *
     *
     * @param pre 键值前缀
     * @param id 缓存id
     * @param type 实体类类型
     * @param dbFallback 函数式编程，查询数据库的逻辑；Function<ID, R> ID 表示参数类型，R 表示返回值类型
     * @param time 缓存有效时间
     * @param timeUnit 时间单位
     * @param <R> 实体类类型
     * @param <ID> ID 类型
     * @return R 返回取到的实体类
     */
    public <R, ID> R queryWithPassThrough(String pre, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        String key = pre + id;

        //1. 从redis中查询店铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        //2. 缓存命中，分两种情况处理
        //2.1 缓存结果不为空，直接返回
        if (StrUtil.isNotBlank(json)) {
            //("缓存命中！");
            return JSONUtil.toBean(json, type);
        }
        //2.2 缓存结果为空
        if ("".equals(json)) {
            //log.info("虚假id");
            return null;
        }

        //3. 查询数据库
        // 使用用户自己传入的函数逻辑去查询数据库
        R r = dbFallback.apply(id);

        //4. 商户不存在，向redis中存入null值，避免缓存穿透
        if (r == null) {
            //存储null值，默认 2分钟
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        //5. 商户存在，写入redis,并设置超时时间，30分钟
        this.set(key, r, time, timeUnit);
        //log.info("将数据库数据写入缓存");

        //6. 将信息返回给前端
        return r;
    }



    //创建一个线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    /**
     *   不考虑缓存穿透问题
     *   逻辑过期解决缓存击穿
     *
     *   单独的T代表一个类型，而Class<T>和Class<?>代表这个类型所对应的类
     *   方法定义：<ID, R>---fun(ID id, Class<R> type)
     *   方法调用： fun(id, Shop.class)     前面传的就是一个 Long类型id， 后面传的是Shop的Class对象
     *   其中，Long id   Shop shop   传入参数后，ID被泛化为Long类型，R被泛化为Shop类型
     *
     * @param logicPre 带逻辑删除信息的前缀
     * @param lockPre 互斥锁前缀
     * @param id 缓存id
     * @param type 实体类类型
     * @param dbFallback 函数式编程，查询数据库的逻辑；Function<ID, R> ID 表示参数类型，R 表示返回值类型
     * @param time 缓存逻辑过期时间
     * @param timeUnit 时间单位
     * @param <R> 实体类类型
     * @param <ID> ID 类型
     * @return R 返回取到的实体类
     */
    public <R, ID> R queryWithLogicalExpire(String logicPre, String lockPre, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        String key = logicPre + id;
        //1. 从redis中查询店铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        //2. 缓存未命中，返回错误
        if (StrUtil.isBlank(json)) {
            //log.info("缓存未命中！");
            return null;
        }

        //3 缓存命中，判断缓存是否过期
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        //获取存活时间
        LocalDateTime expireTime = redisData.getExpireTime();
        //获取商户信息
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        //3.1 缓存未过期，返回店铺信息
        // isAfter 存活时间在当前时间之后，说明没有过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //log.info("缓存没有过期！");
            return r;
        }
        //3.2 缓存过期，尝试获取互斥锁
        //log.info("缓存过期！");
        String lockKey = lockPre + id;
        //锁的时间默认为 10S
        boolean isLock = tryGetLock(lockKey, LOCK_SHOP_TTL);


        //4. 互斥锁获取
        //4.1 key 成功，开启独立线程
        if (isLock) {
            //log.info("获取锁成功！");
            //5. 独立线程
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                //log.info("开启独立线程");
                //重建缓存,是否锁
                try {
                    //5.1 查询数据库
                    //5.2 数据写入Redis,并设置逻辑过期时间
                    saveObject2Redis(lockKey, id, dbFallback, time, timeUnit);
                   //log.info("更新缓存信息成功！");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //5.3 释放互斥锁
                    unLock(lockKey);
                }
            });
        }
        //4.2 成功+失败，返回-旧的-信息
        return r;
    }



    /**
     * 获取互斥锁
     * @param redisKey 加锁的键值
     * @param timeOut ttl有效期
     * @return 成功返回true，失败返回false
     */
    private boolean tryGetLock(String redisKey, Long timeOut) {
        // 通过 setnx 设置 锁 lock, 设置过期时间为10秒钟
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(redisKey, "1", timeOut, TimeUnit.SECONDS);
        // tool 利用工具类自动 拆箱
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 删除互斥锁
     * @param redisKey 删除锁的键值
     */
    private void unLock(String redisKey) {
        stringRedisTemplate.delete(redisKey);
    }


    /**
     *  查询数据库信息，将信息封装后存入redis，设置逻辑过期时间
     * @param logicKey 逻辑key
     * @param id id
     * @param dbFallback 函数式编程，数据库操作
     * @param expireTime 逻辑存活时间
     * @param timeUnit 时间单位
     * @param <R> 实体类对象
     * @param <ID> id 类型
     * @throws InterruptedException 延时异常
     */
    private  <R, ID> void saveObject2Redis(String logicKey, ID id, Function<ID, R> dbFallback, Long expireTime, TimeUnit timeUnit) throws InterruptedException {
        //查数据库
        R r = dbFallback.apply(id);

        //模拟缓存延时
        Thread.sleep(200);

        //存入缓存,并设置逻辑过期时间
        this.setLogic(logicKey + id, r, expireTime, timeUnit);

    }
}
