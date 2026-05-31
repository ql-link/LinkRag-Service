package com.qingluo.link.api;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;


@SpringBootApplication
@MapperScan("com.qingluo.link.mapper")
@ComponentScan({"com.qingluo.link.service", "com.qingluo.link.core", "com.qingluo.link.components", "com.qingluo.link.api"})
public class LinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinkApplication.class, args);
    }
}
