package com.xzdp.utils.Redis.Lock;

/**
 * 定义Redis分布式锁 的获取和释放
 */
public interface ILock {

    /**
     * 尝试获取锁
     * @param timeoutSec 锁的过期时间
     * @return 释放加锁成功
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();

}
