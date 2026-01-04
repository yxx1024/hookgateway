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

    // Algorithm mandated by WeChat Pay V3
    private static final String ALGORITHM = "SHA256withRSA";

    // Simple JSON parser dependency isn't needed if we do simple string check or
    // use ObjectMapper if available.
    // Trying to be dependency-light for the snippet, but Spring Boot has Jackson.
    // Let's use Jackson for robustness.
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

        // 1. Extract Headers
        String timestamp = extractHeaderValue(event.getHeaders(), HEADER_TIMESTAMP);
        String nonce = extractHeaderValue(event.getHeaders(), HEADER_NONCE);
        String signature = extractHeaderValue(event.getHeaders(), HEADER_SIGNATURE);
        String serial = extractHeaderValue(event.getHeaders(), HEADER_SERIAL); // New: Serial

        if (timestamp == null || nonce == null || signature == null) {
            log.warn("WeChat verification failed: Missing required headers (Timestamp/Nonce/Signature)");
            return false;
        }

        // Fix: Replay Attack Protection
        try {
            long eventTime = Long.parseLong(timestamp);
            long currentTime = System.currentTimeMillis() / 1000;
            // 5 minutes window (300 seconds)
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
            // 2. Construct Signature String
            // Format: Timestamp + "\n" + Nonce + "\n" + Body + "\n"
            String signatureStr = timestamp + "\n"
                    + nonce + "\n"
                    + payload + "\n";

            // 3. Resolve Public Key
            String publicKeyPem = resolvePublicKey(verifySecret, serial);
            if (publicKeyPem == null) {
                log.warn("WeChat verification failed: No matching public key found for serial {}", serial);
                return false;
            }

            // 4. Parse Public Key
            PublicKey publicKey = PemUtils.parsePublicKey(publicKeyPem);

            // 5. Verify Signature
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
        // Check if it's JSON map
        if (verifySecret.startsWith("{") && verifySecret.endsWith("}")) {
            try {
                // Parse JSON: {"SERIAL": "PEM", ...}
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
                // Fallback or fail? If it looks like JSON but fails, it's likely a config
                // error.
                return null;
            }
        } else {
            // Backward compatibility: Treat entire string as single PEM
            // If serial provided, we can't check it match, just use the key.
            return verifySecret;
        }
    }

    // Simple LRU Cache for Nonce to prevent replay (Size 10000)
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

    // Helper to extract header from the raw headers string (Key: Value\nKey: Value)
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
