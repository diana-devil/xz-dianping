package com.xzdp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

//暴露代理对象
@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.xzdp.mapper")
@SpringBootApplication
public class XzDianPingApplication {

    public static void main(String[] args) {
        SpringApplication.run(XzDianPingApplication.class, args);
    }

}
