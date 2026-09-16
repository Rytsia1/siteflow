package com.siteflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.siteflow.mapper")
public class SiteflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(SiteflowApplication.class, args);
    }

}
