package com.example.hookgateway.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 验签策略工厂。
 */
@Component
@RequiredArgsConstructor
public class VerifierFactory {

    private final HmacVerifier hmacVerifier;
    private final WechatPayVerifier wechatPayVerifier;

    /**
     * 根据验签方法获取对应策略。
     *
     * @param method 验签方式
     * @return 验签策略
     */
    public VerifierStrategy getStrategy(String method) {
        if ("HMAC_SHA256".equalsIgnoreCase(method)) {
            return hmacVerifier;
        } else if ("WECHAT_PAY".equalsIgnoreCase(method)) {
            return wechatPayVerifier;
        }
        // 预留：支付宝等其他验签方式
        return null; // NONE 或未知类型时返回 null
    }
}
