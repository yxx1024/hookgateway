package com.example.hookgateway.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 设置页面控制器。
 */
@Controller
public class SettingsController {

    /**
     * 设置页面。
     *
     * @param model 视图模型
     * @return 页面名
     */
    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("currentUri", "/settings");
        return "settings";
    }
}
