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
        // 修复：缓解 SSRF 的 DNS 重绑定
        // 强制 JVM 缓存 DNS 解析 60 秒
        java.security.Security.setProperty("networkaddress.cache.ttl", "60");
        SpringApplication.run(HookGatewayApplication.class, args);
    }

}
