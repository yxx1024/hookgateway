package com.example.hookgateway.security;

import com.example.hookgateway.model.Subscription;
import com.example.hookgateway.model.WebhookEvent;
import com.example.hookgateway.utils.PemUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

@Component
@Slf4j
public class WechatPayVerifier implements VerifierStrategy {

    private static final String HEADER_TIMESTAMP = "Wechatpay-Timestamp";
    private static final String HEADER_NONCE = "Wechatpay-Nonce";
    private static final String HEADER_SIGNATURE = "Wechatpay-Signature";
    private static final String HEADER_SERIAL = "Wechatpay-Serial";

    // 微信支付 V3 要求的算法
    private static final String ALGORITHM = "SHA256withRSA";

    // 使用 Jackson 解析 JSON，兼顾可靠性与简化实现
    @org.springframework.beans.factory.annotation.Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Override
    public boolean verify(WebhookEvent event, Subscription sub) {
        String payload = event.getPayload();
        String verifySecret = sub.getVerifySecret();

        if (payload == null || verifySecret == null) {
            log.warn("WeChat verification skipped: Payload or Verify Secret missing");
            return false;
        }

        // 1. 读取必要请求头
        String timestamp = extractHeaderValue(event.getHeaders(), HEADER_TIMESTAMP);
        String nonce = extractHeaderValue(event.getHeaders(), HEADER_NONCE);
        String signature = extractHeaderValue(event.getHeaders(), HEADER_SIGNATURE);
        String serial = extractHeaderValue(event.getHeaders(), HEADER_SERIAL); // Serial 证书序列号

        if (timestamp == null || nonce == null || signature == null) {
            log.warn("WeChat verification failed: Missing required headers (Timestamp/Nonce/Signature)");
            return false;
        }

        // 修复：防重放
        try {
            long eventTime = Long.parseLong(timestamp);
            long currentTime = System.currentTimeMillis() / 1000;
            // 5 分钟窗口（300 秒）
            if (Math.abs(currentTime - eventTime) > 300) {
                log.warn("WeChat verification failed: Timestamp {} is outside of 5min window (current: {})", timestamp,
                        currentTime);
                return false;
            }
        } catch (NumberFormatException e) {
            log.warn("WeChat verification failed: Invalid timestamp format: {}", timestamp);
            return false;
        }

        if (isNonceReplayed(nonce)) {
            log.warn("WeChat verification failed: Nonce {} detected as replay", nonce);
            return false;
        }

        try {
            // 2. 组装签名串
            // 格式：Timestamp + "\n" + Nonce + "\n" + Body + "\n"
            String signatureStr = timestamp + "\n"
                    + nonce + "\n"
                    + payload + "\n";

            // 3. 选择匹配的公钥
            String publicKeyPem = resolvePublicKey(verifySecret, serial);
            if (publicKeyPem == null) {
                log.warn("WeChat verification failed: No matching public key found for serial {}", serial);
                return false;
            }

            // 4. 解析公钥
            PublicKey publicKey = PemUtils.parsePublicKey(publicKeyPem);

            // 5. 验证签名
            Signature verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(publicKey);
            verifier.update(signatureStr.getBytes(StandardCharsets.UTF_8));

            byte[] decodedSignature = Base64.getDecoder().decode(signature);
            return verifier.verify(decodedSignature);

        } catch (IllegalArgumentException e) {
            log.error("WeChat verification error: Invalid Base64 or Key format", e);
            return false;
        } catch (Exception e) {
            log.error("WeChat verification unexpected error", e);
            return false;
        }
    }

    private String resolvePublicKey(String verifySecret, String serial) {
        verifySecret = verifySecret.trim();
        // 判断是否为 JSON 映射
        if (verifySecret.startsWith("{") && verifySecret.endsWith("}")) {
            try {
                // 解析 JSON：{"SERIAL": "PEM", ...}
                java.util.Map<String, String> keys = objectMapper.readValue(verifySecret,
                        new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, String>>() {
                        });

                if (serial == null) {
                    log.warn(
                            "WeChat verification: Multi-cert configured but no Wechatpay-Serial header found. Cannot select key.");
                    return null;
                }

                String pem = keys.get(serial);
                if (pem == null) {
                    log.warn("WeChat verification: Key not found for serial '{}'. Available serials: {}", serial,
                            keys.keySet());
                }
                return pem;
            } catch (Exception e) {
                log.error("Failed to parse verifySecret as JSON map", e);
                // 若看起来是 JSON 但解析失败，通常是配置错误
                return null;
            }
        } else {
            // 兼容旧配置：整串视为单个 PEM
            // 若有 serial 也无法匹配，仅直接使用
            return verifySecret;
        }
    }

    // 简易 LRU 缓存用于防重放（容量 10000）
    private static final java.util.Set<String> PROCESSED_NONCES = java.util.Collections.synchronizedSet(
            java.util.Collections.newSetFromMap(new java.util.LinkedHashMap<String, Boolean>() {
                protected boolean removeEldestEntry(java.util.Map.Entry<String, Boolean> eldest) {
                    return size() > 10000;
                }
            }));

    private boolean isNonceReplayed(String nonce) {
        if (PROCESSED_NONCES.contains(nonce)) {
            return true;
        }
        PROCESSED_NONCES.add(nonce);
        return false;
    }

    // 从原始 headers 字符串中提取 Header（Key: Value\nKey: Value）
    private String extractHeaderValue(String allHeaders, String headerName) {
        if (allHeaders == null || headerName == null)
            return null;
        String[] lines = allHeaders.split("\n");
        for (String line : lines) {
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim();
                if (key.equalsIgnoreCase(headerName)) {
                    return line.substring(colonIndex + 1).trim();
                }
            }
        }
        return null;
    }
}
