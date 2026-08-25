package com.healthkb;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.healthkb.mapper")
public class HealthKbApplication {

    public static void main(String[] args) {
        SpringApplication.run(HealthKbApplication.class, args);
    }
}
