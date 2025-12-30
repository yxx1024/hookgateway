package com.example.hookgateway.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 监控页面控制器
 */
@Controller
public class MonitoringController {

    @GetMapping("/monitoring")
    public String monitoring() {
        return "monitoring";
    }
}
