package com.example.hookgateway.controller;

import com.example.hookgateway.model.Subscription;
import com.example.hookgateway.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionRepository repository;

    @GetMapping
    public String index(Model model) {
        model.addAttribute("subscriptions", repository.findAll());
        model.addAttribute("currentUri", "/subscriptions");
        return "subscriptions";
    }

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

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        repository.deleteById(id);
        return "redirect:/subscriptions";
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id) {
        repository.findById(id).ifPresent(sub -> {
            sub.setActive(!sub.isActive());
            repository.save(sub);
        });
        return "redirect:/subscriptions";
    }
}
