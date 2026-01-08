package com.example.hookgateway.security;

import com.example.hookgateway.model.Subscription;
import com.example.hookgateway.model.WebhookEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * HMAC-SHA256 验签实现。
 */
@Component
@Slf4j
public class HmacVerifier implements VerifierStrategy {

    private static final String HMAC_SHA256 = "HmacSHA256";

    /**
     * 校验 HMAC 签名。
     *
     * @param event 事件
     * @param sub   订阅配置
     * @return 校验通过返回 true
     */
    @Override
    public boolean verify(WebhookEvent event, Subscription sub) {
        String payload = event.getPayload();
        String secret = sub.getVerifySecret();
        String signatureHeaderName = sub.getSignatureHeader();

        if (payload == null || secret == null || signatureHeaderName == null) {
            return false;
        }

        // 从请求头中提取签名
        String signature = extractHeaderValue(event.getHeaders(), signatureHeaderName);
        if (signature == null) {
            log.warn("HMAC verification failed: Signature header '{}' not found", signatureHeaderName);
            return false;
        }

        try {
            // 1. 计算期望的 HMAC
            String expectedHash = calculateHmac(payload, secret);

            // 2. 规范化签名（去掉常见前缀，如 "sha256="）
            String cleanSignature = signature;
            if (signature.startsWith("sha256=")) {
                cleanSignature = signature.substring(7);
            }

            // 3. 常量时间比较，防止计时攻击
            return java.security.MessageDigest.isEqual(
                    cleanSignature.getBytes(StandardCharsets.UTF_8),
                    expectedHash.getBytes(StandardCharsets.UTF_8));

        } catch (Exception e) {
            log.error("HMAC verification error", e);
            return false;
        }
    }

    /**
     * 从原始请求头中提取指定请求头值。
     *
     * @param allHeaders 原始请求头
     * @param headerName 请求头名
     * @return 请求头值
     */
    private String extractHeaderValue(String allHeaders, String headerName) {
        if (allHeaders == null || headerName == null)
            return null;
        // 简单查找逻辑
        String[] lines = allHeaders.split("\n");
        for (String line : lines) {
            // Header 格式: "Key: Value"
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

    /**
     * 计算 HMAC 值。
     *
     * @param data 原文
     * @param key  密钥
     * @return HMAC 十六进制字符串
     */
    private String calculateHmac(String data, String key) throws NoSuchAlgorithmException, InvalidKeyException {
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
        Mac mac = Mac.getInstance(HMAC_SHA256);
        mac.init(secretKey);
        byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hmacBytes);
    }

    /**
     * 将字节数组转为十六进制字符串。
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1)
                hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
