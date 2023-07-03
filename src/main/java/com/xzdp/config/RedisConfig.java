package com.xzdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 使用redisson 操作Redis的配置
 *  最好使用java代码配置的方式，不要使用yml文件配置
 */
@Configuration
public class RedisConfig {

    // Centos7的Redis结点
    private static final String reidsIp = "192.168.159.100:6379";
    //使用docker容器启动两个 redis结点
    private static final String reidsIp2 = "192.168.159.100:1234";
    private static final String reidsIp3 = "192.168.159.100:1235";


    /**
     * 配置单个Redis结点的Redisson客户端
     * @return Redisson客户端
     */
    @Bean
    public RedissonClient redissonClient() {
        //配置
        Config config = new Config();
        // 单点启用
        config.useSingleServer().setAddress("redis://" + reidsIp);
        //创建RedissonClient 对象
        return Redisson.create(config);

    }

    /**
     * 配置单个Redis结点的Redisson客户端
     * @return Redisson客户端
     */
//    @Bean
//    public RedissonClient redissonClient2() {
//        //配置
//        Config config = new Config();
//        // 单点启用
//        config.useSingleServer().setAddress("redis://" + reidsIp2);
//        //创建RedissonClient 对象
//        return Redisson.create(config);
//    }

    /**
     * 配置单个Redis结点的Redisson客户端
     * @return Redisson客户端
     */
//    @Bean
//    public RedissonClient redissonClient3() {
//        //配置
//        Config config = new Config();
//        // 单点启用
//        config.useSingleServer().setAddress("redis://" + reidsIp3);
//        //创建RedissonClient 对象
//        return Redisson.create(config);
//    }
}
