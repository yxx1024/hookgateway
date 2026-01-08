package com.example.hookgateway.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 监控页面控制器
 */
@Controller
public class MonitoringController {

    /**
     * 监控页面。
     *
     * @param model 视图模型
     * @return 页面名
     */
    @GetMapping("/monitoring")
    public String monitoring(Model model) {
        model.addAttribute("currentUri", "/monitoring");
        return "monitoring";
    }
}
