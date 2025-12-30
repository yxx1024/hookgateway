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

    // Algorithm mandated by WeChat Pay V3
    private static final String ALGORITHM = "SHA256withRSA";

    @Override
    public boolean verify(WebhookEvent event, Subscription sub) {
        String payload = event.getPayload();
        // Here verifySecret stores the Platform Public Key (PEM format)
        String publicKeyPem = sub.getVerifySecret();

        if (payload == null || publicKeyPem == null) {
            log.warn("WeChat verification skipped: Payload or Public Key missing");
            return false;
        }

        // 1. Extract Headers
        String timestamp = extractHeaderValue(event.getHeaders(), HEADER_TIMESTAMP);
        String nonce = extractHeaderValue(event.getHeaders(), HEADER_NONCE);
        String signature = extractHeaderValue(event.getHeaders(), HEADER_SIGNATURE);

        if (timestamp == null || nonce == null || signature == null) {
            log.warn("WeChat verification failed: Missing required headers (Timestamp/Nonce/Signature)");
            return false;
        }

        try {
            // 2. Construct Signature String
            // Format: Timestamp + "\n" + Nonce + "\n" + Body + "\n"
            String signatureStr = timestamp + "\n"
                    + nonce + "\n"
                    + payload + "\n";

            // 3. Parse Public Key
            PublicKey publicKey = PemUtils.parsePublicKey(publicKeyPem);

            // 4. Verify Signature
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
