package com.example.hookgateway.security;

import com.example.hookgateway.model.Subscription;
import com.example.hookgateway.model.WebhookEvent;

public interface VerifierStrategy {
    /**
     * 使用完整上下文校验 Webhook 签名。
     *
     * @param event 包含请求头与请求体的事件
     * @param sub   包含密钥配置的订阅
     * @return 校验通过返回 true，否则返回 false
     */
    boolean verify(WebhookEvent event, Subscription sub);
}
