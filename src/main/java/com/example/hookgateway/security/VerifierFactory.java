package com.example.hookgateway.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VerifierFactory {

    private final HmacVerifier hmacVerifier;
    private final WechatPayVerifier wechatPayVerifier;

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
