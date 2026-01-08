package com.example.hookgateway.utils;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * PEM 工具类。
 */
public class PemUtils {

    /**
     * 解析 PEM 格式公钥。
     *
     * @param pem PEM 字符串
     * @return 公钥对象
     * @throws Exception 解析失败时抛出
     */
    public static PublicKey parsePublicKey(String pem) throws Exception {
        if (pem == null) {
            throw new IllegalArgumentException("PEM string cannot be null");
        }

        // 去除 PEM 头尾
        String publicKeyPEM = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", ""); // 更稳妥地去除空白字符

        byte[] encoded = Base64.getDecoder().decode(publicKeyPEM);

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
        return keyFactory.generatePublic(keySpec);
    }
}
