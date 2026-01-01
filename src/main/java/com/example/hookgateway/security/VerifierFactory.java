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
        // Future: ALIPAY, etc.
        return null; // Return null if NONE or unknown
    }
}
