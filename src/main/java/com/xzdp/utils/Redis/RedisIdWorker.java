package com.xzdp.utils.Redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 使用Redis实现全局唯一id
 * key 时间操作API + 位运算
 */
@Component
public class RedisIdWorker {

    /**
     * 2022-01-01 0:0:0
     * 时间戳
     */
    private static final long BEGIN_TIME = 1640995200L;

    /**
     * 序列号长度
     * 暂定为32位-- 二进制位
     */
    private static final long COUNT_BITS = 32;



    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix) {
        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        //借助API 生成 long型id
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIME;

        //2. 生成序列号
        //2.1 获取日期，精确到天-- 为了键的拼接，避免超过上限;方便订单查询
        // 将时间进行格式转换
        String day = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //2.2 使用Redis的自增长实现
        // 不用考虑拆箱为空指针的情况，当新的一天key不存在时，会自动创建一个
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + day);

        //3. 拼接并返回，使用位运算
        // 时间戳左移，位数为序列号长度，形成高32位为时间戳，低32位为0
        // 与最大为32的序列号做或运算，拼接在一起
        return timestamp << COUNT_BITS | count;
    }

//    public static void main(String[] args) {
//        LocalDateTime time = LocalDateTime.of(2022, 1, 1, 0, 0, 0);
//        System.out.println(time);
//        long second = time.toEpochSecond(ZoneOffset.UTC);
//        //1640995200
//        System.out.println(second);
//    }




























}
