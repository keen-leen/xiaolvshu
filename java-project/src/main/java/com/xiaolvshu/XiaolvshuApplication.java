package com.xiaolvshu;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

@SpringBootApplication
@MapperScan("com.xiaolvshu.mapper")
public class XiaolvshuApplication {

    public static void main(String[] args) {
        SpringApplication.run(XiaolvshuApplication.class, args);
    }
}
