package com.xzdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xzdp.dto.Result;
import com.xzdp.entity.VoucherOrder;
import com.xzdp.mapper.VoucherOrderMapper;
import com.xzdp.service.ISeckillVoucherService;
import com.xzdp.service.IVoucherOrderService;
import com.xzdp.utils.Redis.RedisIdWorker;
import com.xzdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.xzdp.utils.Constants.RedisConstants.*;

/**
 * <p>
 *      优惠券秒杀业务实现2
 *
 *   使用Redis+异步处理进行改进
 *   将抢单和数据库操作分开，异步进行
 *
 *   1.使用lua脚本完成库存判断和一人一单判断
 *   2.使用redis的stream作为消息队列（mq）,来进行异步的消息传递，进行订单创建和库存扣减工作
 *
 * </p>
 *
 * @author diane
 * @since 2021-12-22
 */
@Slf4j
//@Service
public class VoucherOrderServiceImpl3 extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private RedissonClient redissonClient;

    /**
     * 获取该类的动态代理对象，用成员变量的方式，让异步线程获取
     */
    private IVoucherOrderService proxy;



    /**
     * 使用静态代码块的方式引入lua脚本
     * 返回值类型 数字必须为Long类型， Integer类型会报错
     */
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        //spring 提供的ClassPathResource，默认文件夹是 resources文件
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        // key 设置返回值类型 数字必须为Long类型
        SECKILL_SCRIPT.setResultType(Long.class);
    }


    //异步处理线程池-创建一个单线程
    private final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    /**
     * 将任务提交给线程处理
     * 该注解，是当类初始化完毕后，就自动执行，所以任务就自动提交了
     * 我们的线程会一直盯着redis的stream队列，进行异步消息的获取
     */
    @PostConstruct
    private void init() {
        //提交任务给线程
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    /**
     *   创建线程任务
     */
    private class VoucherOrderHandler implements Runnable {

        private static final String GROUPNAME = "g1";
        private static final String CONSUMERNAME = "c1";

        /**
         *  监听队列中的消息，并进行处理
         * 1. 尝试获取队列中的消息
         * 2. 判断消息获取是否成功，失败则代表没有消息，继续循环
         * 3. 成功，则处理队列消息，提取出订单消息
         * 4. 处理订单业务，并返回ack
         * 5. 消息处理无异常，则继续循环；若有异常，则去处理pendinglist中的信息
         */
        @Override
        public void run() {
            while (true) {
                //log.info("我在监听stream消息~！！");
                try {
                    //1. 获取队列中的消息  xreadgroup group g1 c1 count 1 block 2000 streams stream.orders >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from(GROUPNAME, CONSUMERNAME),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(QUEUENAME, ReadOffset.lastConsumed())
                    );
                    //2. 判断消息获取是否成功
                    if (list == null || list.isEmpty()) {
                        //2.1 失败，说明没有消息，继续循环
                        continue;
                    }

                    //3 解析队列中的订单消息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    log.info("捕获订单！{}",voucherOrder);

                    //4 处理订单事务，并返回ACK确认 xack stream.orders g1 id
                    handleVoucherOrder(voucherOrder);
                    stringRedisTemplate.opsForStream().acknowledge(QUEUENAME, GROUPNAME, record.getId());
                    log.info("订单处理完成！已经返回ACK");
                } catch (Exception e) {
                    //5.业务处理异常，消息会存到pendinglist中
                    log.error("业务异常！",e);
                    handlePendingList();
                }
            }
        }

        /**
         * 处理因为异常，存到pendinglist中的消息，与上面逻辑基本一致
         */
        private void handlePendingList() {
            log.info("我在处理pendinglist的消息~！！");
            int count = 0;
            while (true) {
                try {
                    //1. 获取pendinglist中的消息  xreadgroup group g1 c1 count 1  streams stream.orders 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from(GROUPNAME, CONSUMERNAME),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(QUEUENAME, ReadOffset.from("0"))
                    );
                    //2. 判断消息获取是否成功
                    if (list == null || list.isEmpty()) {
                        //2.1 失败，说明没有消息，结束循环
                        break;
                    }

                    //3 解析队列中的订单消息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);

                    //4 处理订单事务，并返回ACK确认 xack stream.orders g1 id
                    handleVoucherOrder(voucherOrder);
                    stringRedisTemplate.opsForStream().acknowledge(QUEUENAME, GROUPNAME, record.getId());
                } catch (Exception e) {
                    //业务处理异常，消息会存到pendinglist中
                    log.error("pendinglist处理发生异常！",e);
                    count++;
                    if (count > 10) {
                        log.info("异常次数太多，交易人工处理！");
                        break;
                    }
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
    }




    /**
     *  异步创建订单
     *  1.要注意一个事情，就是 这个方法是由另外开启的线程执行的，不是由主线程执行的
     *       1.1userId不能从 UserHolder中获取，可以从订单信息中获取
     *       1.2该类的动态代理对象是获取不到的，只能从主线程中获取，然后通过成员变量的方式获取
     *   2. 这里的锁可以不用加了，因为在Redis中已经执行过了，确保不会线程安全问题
     *      这里还是保留原来的加锁操作，为了进一步的保险吧，虽然没啥用…………
     *
     * @param voucherOrder 订单信息
     */
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        //创建锁对象，使用构造函数注入参数
        // 将锁的范围，减少到 每个用户上
        RLock lock = redissonClient.getLock(LOCK_VOUCHER_ORDER + userId);
        //无参数，默认失败不等待；锁的存在时间无限长，直到锁释放
        boolean isLock = lock.tryLock();
        if (!isLock) {
            // 获取锁失败，直接返回失败
            log.info("不允许重复下单！");
            return;
        }
        try {
            //执行事务
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            //释放锁
            lock.unlock();
        }

    }



    /**
     * 完成秒杀优惠券抢购---主线程
     *
     *  -- redis
     *  1. 调用lua脚本，执行库存判断，一人一单判断
     *  2. 判断执行结果是否为0，非0返回错误，是0则执行异步扣减库存和创建订单操作
     *  3. 给成员变量 proxy赋值，供其他线程调用
     *  4. 返回订单id，给前端
     *
     *
     * @param voucherId 优惠券id
     * @return 标准结果
     */
    @Override
    public Result seckillVoucher(Long voucherId, HttpServletRequest request) {
        //0. 获取参数
        Long userId = UserHolder.getUser().getId();
        // 获取订单全局唯一id
        long orderId = redisIdWorker.nextId(VOUCHER_ORDER);

        //1. 执行lua脚本
        Long res = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        int r = res.intValue();

        //2. 判断结果是否为0
        if (r != 0) {
            //不为0,代表没有购买资格；1代表库存不足，2代表重复下单
            return  Result.fail(r == 1 ? "库存不足！" : "重复下单！");
        }

        //3. 获取该类的动态代理对象，供异步线程使用
        proxy = (IVoucherOrderService)AopContext.currentProxy();

        //4.返回订单id
        return Result.ok(orderId);
    }





    /**
     *  创建秒杀优惠券订单
     *  扣减库存和保存订单
     *
     *
     *  1. 根据优惠券id和用户id查询数据库中订单
     *      1.1 订单存在，返回失败
     *      1.2 订单不存在，扣减库存 -- 乐观锁
     *      补充说明：其实这里的一人一单查询和乐观锁都没有必要，因为这里只需要执行扣单操作即可，前面已经在Redis中处理过了
     *  2. 将订单保存的数据库中
     *
     *
     * @param voucherOrder 订单信息
     *
     */
    @Transactional
    @Override
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();

        //1. 根据优惠券id和用户id查询数据库中订单
        int count = lambdaQuery().eq(VoucherOrder::getUserId, userId)
                .eq(VoucherOrder::getVoucherId, voucherId).count();
//        1.1 订单存在，返回失败
        if (count > 0) {
            log.error("你已经抢购了，把机会留给别人吧！");
        }

        //1.2 扣减库存-乐观锁
        //写sql方式，修改库存字段
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId).gt("stock", 0)
                .update();
        if (!success) {
            log.error("很遗憾，优惠券已经被抢完！");
        }

        //2.保存订单信息到数据库中
        save(voucherOrder);
    }


    /**
     * 实现接口的方法
     * @param voucherId 优惠券id
     * @param  userId 用户id
     * @return 这个方法什么不做，只是为了实现方法
     */
    public Result createVoucherOrder(Long voucherId, Long userId) {
        return Result.ok("ok！");
    }
}
