package com.xzdp.service.impl;

import com.xzdp.dto.Result;
import com.xzdp.entity.SeckillVoucher;
import com.xzdp.entity.VoucherOrder;
import com.xzdp.mapper.VoucherOrderMapper;
import com.xzdp.service.ISeckillVoucherService;
import com.xzdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xzdp.utils.Redis.Lock.SimpleRedisLock;
import com.xzdp.utils.Redis.RedisIdWorker;
import com.xzdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

import static com.xzdp.utils.Constants.RedisConstants.*;

/**
 * <p>
 *  单线程操作，只有数据库，实现秒杀优惠券
 *  优点：加锁保证一人一单，扣减库存线程安全
 *  缺点：速度较慢，效率比较低。
 *
 *  改进：使用Redis+异步处理进行改进
 *  改进代码在  VoucherOrderServiceImpl2中实现
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
//@Service  --不加入容器
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private RedissonClient redissonClient;

    /**
     * 完成秒杀优惠券抢购
     *
     *  -- 秒杀优化券表-tb_seckill_voucher
     *  1. 查询秒杀优惠券信息
     *  2. 判断秒杀是否开始
     *  3. 判断秒杀是否结束
     *  4. 判断库存是否充足
     *
     *  -- 保证一人一单，加悲观锁
     *  5. 根据优惠券id和用户id查询数据库中订单
     *      5.1 订单存在，返回失败
     *      5.2 订单不存在，扣减库存 -- 乐观锁
     *
     *  -- 优惠券订单表-tb_voucher_order
     *  6. 创建订单 -- 全局唯一ID
     *  7. 返回订单id
     * @param voucherId 优惠券id
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId, HttpServletRequest request) {
        //1. 查询秒杀优惠券信息
        SeckillVoucher seckillVoucher = seckillVoucherService.lambdaQuery().eq(SeckillVoucher::getVoucherId, voucherId).one();

        LocalDateTime now = LocalDateTime.now();
        //2. 判断秒杀是否开始
        LocalDateTime beginTime = seckillVoucher.getBeginTime();
        if (beginTime.isAfter(now)) {
            return Result.fail("抢购还未开始，请耐心等待！");
        }
        //3. 判断秒杀是否结束
        LocalDateTime endTime = seckillVoucher.getEndTime();
        if (endTime.isBefore(now)) {
            return Result.fail("抢购已经结束！");
        }
        //4. 判断库存是否充足
        int stock = seckillVoucher.getStock();
        if (stock <= 0) {
            return Result.fail("很遗憾，优惠券已经被抢完！");
        }

        Long userId = UserHolder.getUser().getId();

        // 一人一单，加锁操作
        //l1. 加synchronized锁，实现单体加锁
//        return pessimisticLock(voucherId, userId);
        //l2. 利用redis的 setnx实现简单分布式锁
//        return simpleRedisLock(voucherId, userId);
        //l3. 使用Redisson 实现加锁操作
        return redissonLock(voucherId, userId);
    }


    /**
     * l3. 使用Redisson 实现加锁操作
     *
     * @param voucherId 优惠券id
     * @param userId 用户id
     * @return 标准结果
     */
    private Result redissonLock(Long voucherId, Long userId) {
        //创建锁对象，使用构造函数注入参数
        // 将锁的范围，减少到 每个用户上
        RLock lock = redissonClient.getLock(LOCK_VOUCHER_ORDER + userId);
        //无参数，默认失败不等待；锁的存在时间无限长，直到锁释放
        boolean isLock = lock.tryLock();
        if (!isLock) {
            // 获取锁失败，直接返回失败
            return Result.fail("不允许重复下单！");
        }
        try {
            //执行事务
            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId, userId);
        } finally {
            //释放锁
            lock.unlock();
        }
    }




    /**
     *  l2. 利用redis的 setnx 实现简单分布式锁
     *
     *  锁的作用范围是tomcat集群
     *
     * @param voucherId 优惠券id
     * @param userId 用户id
     * @return 标准结果
     */
    private Result simpleRedisLock(Long voucherId, Long userId) {
        //创建锁对象，使用构造函数注入参数
        // 将锁的范围，减少到 每个用户上
        SimpleRedisLock lock = new SimpleRedisLock(LOCK_VOUCHER_ORDER + userId, stringRedisTemplate);
        boolean isLock = lock.tryLock(1200);
        if (!isLock) {
            // 获取锁失败，直接返回失败
            return Result.fail("不允许重复下单！");
        }
        try {
            //执行事务
            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId, userId);
        } finally {
            //释放锁
            lock.unlock();
        }
    }




    /**
     *  l1.加 synchronized 锁
     *
     *  锁的作用范围是每个jvm，即每台tomcat服务器
     *  适用于单台 tomcat服务器，不适用于tomcat集群
     *
     *
     *  key 悲观锁，线程安全，事务
     *
     *  1. 直接在方法上加锁 ————public synchronized Result
     *      问题：
     *          1.1 这样的话，锁只有一把，锁住了整个方法，锁的粒度较粗
     *          1.2 在业务并发执行时，整个方法却是串行的，效率较低
     *      优点：
     *          1.3 在方法上加锁，先提交事务，在释放锁，没有事务问题
     *
     *  2. 按照用户id加悲观锁，一个用户一把锁 ————synchronized (userId.toString().intern()) {}
     *      优点：
     *          2.1 一个用户一把锁，仅仅对单个用户串行，效率较高
     *          2.2 userId.toString() 是直接new一个对象返回，即使是同一个用户id，得到的却是不同的对象
     *          userId.toString().intern()  获取常量池中的String数据，当值不变时，返回的是同一个对象
     *      问题：
     *          2.3 在方法内部加锁，先释放锁，在提交事务，会出现事务问题；锁的范围小了，应该是锁住整个方法
     *
     *  3. 在调用方法外部加锁，锁住带事务的整个方法中
     *    synchronized (userId.toString().intern()) {
     *              // 使用this，当前对象调用方法
     *             return this.createVoucherOrder(voucherId);}
     *     优点： 在方法 外部加锁，保证先提交事务，在释放锁，确保线程安全
     *     问题： 使用this.调用方法，拿到的是当前类的对象，不是其代理对象，没有事务功能。
     *           而事务要想生效，需要拿到当前类的动态代理对象才行。
     *
     *  4. 获取代理对象，使用代理对象执行 创建订单方法
     *      synchronized (userId.toString().intern()) {
     *          // 拿到当前对象（IVoucherOrderService）的代理对象
     *         IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
     *         return proxy.createVoucherOrder(voucherId);}
     *      4.1 使用动态对象执行方法，才能使事务成功
     *      4.2 添加依赖--aspectjweaver
     *      4.3 开启动态代理暴露--@EnableAspectJAutoProxy(exposeProxy = true)
     *
     *
     * @param voucherId 优惠券id
     * @param userId 用户id
     * @return 标准结果
     */
    private Result pessimisticLock(Long voucherId, Long userId) {
        //key 在方法 外部加锁，保证先提交事务，在释放锁，确保线程安全
        synchronized (userId.toString().intern()) {
            // key 拿到当前对象（IVoucherOrderService）的代理对象
            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId, userId);
        }
    }



    /**
     *  创建秒杀优惠券订单
     *
     *-- 保证一人一单，加锁
     *  5. 根据优惠券id和用户id查询数据库中订单
     *      5.1 订单存在，返回失败
     *      5.2 订单不存在，扣减库存 -- 乐观锁
     *
     *  -- 优惠券订单表-tb_voucher_order
     *  6. 创建订单 -- 全局唯一ID
     *  7. 返回订单id
     *
     *
     * @param voucherId 优惠券id
     * @param  userId 用户id
     * @return 标准结果集
     */
    @Transactional
    @Override
    public  Result createVoucherOrder(Long voucherId, Long userId) {

        //5. 根据优惠券id和用户id查询数据库中订单
        int count = lambdaQuery().eq(VoucherOrder::getUserId, userId)
                .eq(VoucherOrder::getVoucherId, voucherId).count();
//        5.1 订单存在，返回失败
        if (count > 0) {
            return Result.fail("你已经抢购了，把机会留给别人吧！");
        }

        //5.2 key 扣减库存 --使用写sql方式 --乐观锁
        // 因为表里有很多关于时间的字段，你直接用实体类去修改，一下子修改所有字段的值，修改时间为之前查询到优惠券信息的修改时间
        // 更推荐使用写sql的方式，修改库存一个字段

//        seckillVoucher.setStock(stock - 1);
//        seckillVoucherService.updateById(seckillVoucher);

        //写sql方式，修改库存字段
        boolean success = seckillVoucherService.update()
                //set stock = stock - 1
                .setSql("stock = stock - 1")
                //where id = ？ and stock = ?
                //.eq("voucher_id", voucherId).eq("stock",seckillVoucher.getStock()) // 线程太安全了，失败率太高了

                // 将判断库存相等改为 判断库存大于0
                //where id = ? and stock > 0
                .eq("voucher_id", voucherId).gt("stock", 0)
                .update();
        if (!success) {
            return Result.fail("很遗憾，优惠券已经被抢完！");
        }


        //6. 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //6.1 获取全局唯一id
        long orderId = redisIdWorker.nextId(VOUCHER_ORDER);
        voucherOrder.setId(orderId);
        //6.2 获取用户id--从redis中查
//        String token = request.getHeader(TOKEN_HEADER);
//        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
//        Long userId = Long.parseLong(entries.get("id").toString());
        //6.2 获取用户id--从本地线程中查
//        Long userId = UserHolder.getUser().getId();
        voucherOrder.setUserId(userId);
        //6.3 封装订单表，存入数据库
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);

        //7. 返回订单id
        return Result.ok(orderId);
    }


    /**
     * 实现接口的方法
     * @param voucherOrder 订单信息
     */
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        return;
    }
}
