package com.example.hookgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableRetry
@EnableScheduling
public class HookGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(HookGatewayApplication.class, args);
    }

}
