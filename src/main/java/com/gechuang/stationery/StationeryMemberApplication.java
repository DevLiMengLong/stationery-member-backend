package com.gechuang.stationery;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan({
        "com.gechuang.stationery.member.mapper",
        "com.gechuang.stationery.mainflow"
})
@SpringBootApplication
public class StationeryMemberApplication {

    public static void main(String[] args) {
        SpringApplication.run(StationeryMemberApplication.class, args);
    }
}
