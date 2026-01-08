package com.example.hookgateway.controller;

import com.example.hookgateway.model.Subscription;
import com.example.hookgateway.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * 订阅管理控制器。
 */
@Controller
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionRepository repository;

    /**
     * 订阅列表页面。
     *
     * @param model 视图模型
     * @return 页面名
     */
    @GetMapping
    public String index(Model model) {
        model.addAttribute("subscriptions", repository.findAll());
        model.addAttribute("currentUri", "/subscriptions");
        return "subscriptions";
    }

    /**
     * 创建订阅。
     *
     * @param source          来源
     * @param targetUrl       目标地址
     * @param filterType      过滤类型
     * @param filterRule      过滤规则
     * @param verifyMethod    验签方法
     * @param verifySecret    验签密钥
     * @param signatureHeader 签名请求头
     * @param destinationType 目标类型
     * @param tunnelKey       隧道 Key
     * @return 重定向路径
     */
    @PostMapping
    public String create(@RequestParam String source,
            @RequestParam(required = false) String targetUrl,
            @RequestParam(defaultValue = "NONE") String filterType,
            @RequestParam(required = false) String filterRule,
            @RequestParam(defaultValue = "NONE") String verifyMethod,
            @RequestParam(required = false) String verifySecret,
            @RequestParam(required = false) String signatureHeader,
            @RequestParam(defaultValue = "HTTP") String destinationType,
            @RequestParam(required = false) String tunnelKey) { // 接收前端传入的 key

        String finalTunnelKey = tunnelKey;
        String finalTargetUrl = targetUrl;

        if ("TUNNEL".equalsIgnoreCase(destinationType)) {
            // 前端传入优先，否则生成新 key
            if (finalTunnelKey == null || finalTunnelKey.isEmpty()) {
                // 修复：使用 SecureRandom 提升随机性
                java.security.SecureRandom random = new java.security.SecureRandom();
                byte[] bytes = new byte[24]; // 24 字节 -> Base64 后约 32 字符
                random.nextBytes(bytes);
                finalTunnelKey = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            }
            finalTargetUrl = "tunnel://" + finalTunnelKey; // 满足 DB 非空约束的占位
        } else {
            finalTunnelKey = null; // 非隧道模式清空 key
        }

        Subscription sub = Subscription.builder()
                .source(source)
                .targetUrl(finalTargetUrl)
                .filterType(filterType)
                .filterRule(filterRule)
                .verifyMethod(verifyMethod)
                .verifySecret(verifySecret)
                .signatureHeader(signatureHeader)
                .destinationType(destinationType)
                .tunnelKey(finalTunnelKey)
                .active(true)
                .build();
        repository.save(sub);
        return "redirect:/subscriptions";
    }

    /**
     * 删除订阅。
     *
     * @param id 订阅 ID
     * @return 重定向路径
     */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        repository.deleteById(id);
        return "redirect:/subscriptions";
    }

    /**
     * 启用/禁用订阅。
     *
     * @param id 订阅 ID
     * @return 重定向路径
     */
    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id) {
        repository.findById(id).ifPresent(sub -> {
            sub.setActive(!sub.isActive());
            repository.save(sub);
        });
        return "redirect:/subscriptions";
    }
}
