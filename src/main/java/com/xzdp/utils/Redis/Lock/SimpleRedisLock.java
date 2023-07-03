package com.xzdp.utils.Redis.Lock;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import java.util.Collections;
import java.util.concurrent.TimeUnit;


/**
 * 实现加锁的简单操作
 *  跟在shopService中实现的方法一样
 *  不用spring容器管理，尝试使用构造函数方法创建，调用
 */
//@Component
public class SimpleRedisLock implements ILock {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 加锁或者释放锁的键
     */
    private final String redisKey;

    /**
     *  使用构造函数 给stringRedisTemplate赋值
     *  当然也可以加入Spring， 由容器管理，然后自动注入
     * @param stringRedisTemplate redisAPI
     */
    public SimpleRedisLock(String redisKey, StringRedisTemplate stringRedisTemplate) {
        this.redisKey = redisKey;
        this.stringRedisTemplate = stringRedisTemplate;
    }


    /**
     * 每个线程使用不一样的UUID
     */
    private static final String  ID_PREFIX = UUID.randomUUID().toString(true) + "-";


    /**
     * Redis 脚本
     * 返回值Long类型
     * 以静态代码块注入
     * key 将lua脚本以静态代码块的方式 写在类中，这样lua脚本读取的io流只需要一次，性能较高
     */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        //spring 提供的ClassPathResource，默认文件夹是 resources文件
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        // 设置返回值类型 为Long类型
        UNLOCK_SCRIPT.setResultType(Long.class);
    }



    /**
     *  尝试获取锁
     *  键为 用户传递
     *  值为 线程标识，UUID+线程号
     * @param timeoutSec 锁的过期时间
     * @return true ？ false
     */
    @Override
    public boolean tryLock(long timeoutSec) {
        //获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 获取锁
        Boolean flag = stringRedisTemplate.opsForValue()
                .setIfAbsent(redisKey, threadId, timeoutSec, TimeUnit.SECONDS);
        //避免拆箱 产生空指针
//        return BooleanUtil.isTrue(flag);
        return Boolean.TRUE.equals(flag);
    }


    /**
     * 调用lua脚本执行
     * 判断锁和删除锁 具有原子性
     *
     */
    @Override
    public void unlock() {
        // 调用lua脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(redisKey),
                ID_PREFIX + Thread.currentThread().getId());
    }



//    /**
//     * 删除锁
//     * 删除锁之前判断一下，是不是自己的那把锁，避免误删情况
//     * 不能保证判断锁和删除锁的原子性，存在线程安全问题
//     */
//    @Override
//    public void unlock() {
//        // 获取线程标识
//        String threadId = ID_PREFIX + Thread.currentThread().getId();
//        // 获取锁中线程标识
//        String lockThreadId = stringRedisTemplate.opsForValue().get(redisKey);
//        //标识一致，删除锁
//        if (threadId.equals(lockThreadId)) {
//            stringRedisTemplate.delete(redisKey);
//        }
//        //标识不一致，直接返回
//    }
}
