package com.example.hookgateway.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 登录页面控制器
 */
@Controller
public class LoginController {

    /**
     * 登录页面。
     *
     * @return 页面名
     */
    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
