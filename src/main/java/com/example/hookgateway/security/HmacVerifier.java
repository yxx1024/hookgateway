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

@Component
@Slf4j
public class HmacVerifier implements VerifierStrategy {

    private static final String HMAC_SHA256 = "HmacSHA256";

    @Override
    public boolean verify(WebhookEvent event, Subscription sub) {
        String payload = event.getPayload();
        String secret = sub.getVerifySecret();
        String signatureHeaderName = sub.getSignatureHeader();

        if (payload == null || secret == null || signatureHeaderName == null) {
            return false;
        }

        // Extract signature from headers
        String signature = extractHeaderValue(event.getHeaders(), signatureHeaderName);
        if (signature == null) {
            log.warn("HMAC verification failed: Signature header '{}' not found", signatureHeaderName);
            return false;
        }

        try {
            // 1. Calculate Expected Hash
            String expectedHash = calculateHmac(payload, secret);

            // 2. Normalize input signature (remove common prefixes like "sha256=")
            String cleanSignature = signature;
            if (signature.startsWith("sha256=")) {
                cleanSignature = signature.substring(7);
            }

            // 3. Constant-time comparison to prevent timing attacks
            return java.security.MessageDigest.isEqual(
                    cleanSignature.getBytes(StandardCharsets.UTF_8),
                    expectedHash.getBytes(StandardCharsets.UTF_8));

        } catch (Exception e) {
            log.error("HMAC verification error", e);
            return false;
        }
    }

    private String extractHeaderValue(String allHeaders, String headerName) {
        if (allHeaders == null || headerName == null)
            return null;
        // Simple search logic
        String[] lines = allHeaders.split("\n");
        for (String line : lines) {
            // Header format: "Key: Value"
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

    private String calculateHmac(String data, String key) throws NoSuchAlgorithmException, InvalidKeyException {
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
        Mac mac = Mac.getInstance(HMAC_SHA256);
        mac.init(secretKey);
        byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hmacBytes);
    }

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
