package com.xzdp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzdp.entity.Shop;
import com.xzdp.entity.User;
import com.xzdp.mapper.UserMapper;
import com.xzdp.service.impl.ShopServiceImpl;
import com.xzdp.utils.Redis.RedisIdWorker;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static com.xzdp.utils.Constants.RedisConstants.LOCK_SHOP_TTL;
import static com.xzdp.utils.Constants.RedisConstants.SHOP_GEO_KEY;

/**
 * key 多线程调用，多线程统计
 */
@SpringBootTest
@Slf4j
class XzDianPingApplicationTests {

    @Autowired
    private UserMapper userMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private RedisIdWorker redisIdWorker;

    // 生成线程池
    private ExecutorService es = Executors.newFixedThreadPool(10);

    /**
     * 测试使用redis生成全局唯一id
     */
    @Test
    void testIdWorker() throws InterruptedException {
        //配合await 实现对主进程的阻塞
        // 计数为300
        CountDownLatch latch = new CountDownLatch(300);

        //创建任务
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println(id);
            }
            //执行一次该方法，内部计数减少一次，当计数为0时，await不在阻塞
            latch.countDown();
        };
        //记录开始时间
        long begin = System.currentTimeMillis();
        // 多线程执行任务,提交300次，每提交一个任务开启一个线程
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        //记录结束时间
        long end = System.currentTimeMillis();
        // Redis创建 30000 个id的时间
        System.out.println("time:" + (end - begin));
    }




    /**
     * 测试数据库中是否有该用户
     *  TODO 数据库分页了，存储的数据在第二页，---是不是傻(⊙_⊙)?
     */
    @Test
    void getUser() {
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        qw.eq(User::getPhone, "17854201283");
        User user = userMapper.selectOne(qw);
        System.out.println(user);

    }


    /**
     *  实现缓存预热
      */
    @Test
    void redisPreHot() throws InterruptedException {
        for (long i = 1; i <= 14; i++) {
            shopService.saveShop2Redis(i, LOCK_SHOP_TTL);
        }
    }


    /**
     * 方法1  向Redis缓存中添加店铺位置信息
     *
     * 使用 GeoLocation 一次性 将数据写入Redis
     *
     */
    @Test
    void loadShopData() {
        // 1.查询店铺信息
        List<Shop> list = shopService.list();
        // 2.把店铺分组，按照typeId分组，typeId一致的放到一个集合
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 3.分批完成写入Redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            // 3.1.获取类型id
            Long typeId = entry.getKey();
            String key = SHOP_GEO_KEY + typeId;
            // 3.2.获取同类型的店铺的集合
            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            // 3.3.写入redis GEOADD key 经度 纬度 member
            for (Shop shop : value) {
                // stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }


    /**
     * 方法3
     * 向Redis缓存中添加店铺位置信息
     * 不够优雅
     *
     */
    @Test
    void  addShopGeo() {
        for (int i = 1; i <= 2; i++) {
            List<Shop> shopList = shopService.lambdaQuery().eq(Shop::getTypeId, i).list();
            shopList.stream().map(shop ->
                    stringRedisTemplate
                            .opsForGeo()
                            .add(SHOP_GEO_KEY + shop.getTypeId(),
                                    new Point(shop.getX(), shop.getY()),
                                    shop.getId().toString())).count();
            log.info("类型{}店铺添加成功！", i);
        }
    }

    /**
     * 方法2
     * 向Redis缓存中添加店铺位置信息
     * key 使用stream优雅分组
     *  Stream不调用终结方法，中间的操作不会执行
     */
    @Test
    void loadShopGeo() {
        //1. 查询店铺信息
        List<Shop> shopList = shopService.list();
        //2. 根据typeID 分组
        Map<Long, List<Shop>> shopListMap = shopList.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //3. 分批写入Redis
        for (Map.Entry<Long, List<Shop>> entry : shopListMap.entrySet()) {
            String key = SHOP_GEO_KEY + entry.getKey();
            entry.getValue().stream().map(shop ->
                    stringRedisTemplate.opsForGeo()
                            .add(key,
                                    new Point(shop.getX(), shop.getY()),
                                    shop.getId().toString())).count();
        }
    }


    /**
     * 测试一些stream流的常用方法
     */
    @Test
    void testSkip() {
        List<String> list = new ArrayList<>();
        Collections.addAll(list, "迪丽热巴", "宋远桥", "苏星河", "老子", "庄子", "孙子");
        list.stream().skip(2).forEach(System.out::println);
    }


    /**
     * 利用Redis 实现 UV统计
     */
    @Test
    void testHyperLogLog() {
        String[] values = new String[1000];
        int j = 0;
        for (int i = 0; i < 1000000; i++) {
            j = i % 1000;
            values[j] = "user_" + i;
            if (j == 999) {
                //一次性写入1000条数据
                stringRedisTemplate.opsForHyperLogLog().add("h2l", values);
            }
        }
        //统计用户量
        Long h2l = stringRedisTemplate.opsForHyperLogLog().size("h2l");
        log.info("用户量：{}", h2l);
    }


}
