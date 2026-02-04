package com.xiaolvshu;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.xiaolvshu.mapper")
public class XiaolvshuApplication {

    public static void main(String[] args) {
        SpringApplication.run(XiaolvshuApplication.class, args);
    }
}
