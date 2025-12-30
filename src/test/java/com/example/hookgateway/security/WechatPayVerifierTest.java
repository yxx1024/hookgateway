package com.example.hookgateway.security;

import com.example.hookgateway.model.Subscription;
import com.example.hookgateway.model.WebhookEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;

public class WechatPayVerifierTest {

    private WechatPayVerifier verifier;
    private KeyPair keyPair;
    private String publicKeyPem;

    @BeforeEach
    public void setup() throws Exception {
        verifier = new WechatPayVerifier();

        // Generate RSA KeyPair
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        keyPair = keyGen.generateKeyPair();

        // Convert Public Key to PEM format
        String base64Key = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        publicKeyPem = "-----BEGIN PUBLIC KEY-----\n" + base64Key + "\n-----END PUBLIC KEY-----";
    }

    @Test
    public void testVerifySuccess() throws Exception {
        String payload = "{\"amount\": 100}";
        String timestamp = "1678888888";
        String nonce = "randomNonce";

        // Construct Signature String: timestamp + "\n" + nonce + "\n" + body + "\n"
        String signatureStr = timestamp + "\n" + nonce + "\n" + payload + "\n";

        // Sign with Private Key
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(keyPair.getPrivate());
        sig.update(signatureStr.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(sig.sign());

        // Prepare Event
        String headers = "Wechatpay-Timestamp: " + timestamp + "\n" +
                "Wechatpay-Nonce: " + nonce + "\n" +
                "Wechatpay-Signature: " + signature + "\n";

        WebhookEvent event = WebhookEvent.builder()
                .headers(headers)
                .payload(payload)
                .build();

        Subscription sub = Subscription.builder()
                .verifySecret(publicKeyPem)
                .build();

        Assertions.assertTrue(verifier.verify(event, sub));
    }

    @Test
    public void testVerifyFail_TamperedPayload() throws Exception {
        String payload = "{\"amount\": 100}";
        String tamperedPayload = "{\"amount\": 999}";
        String timestamp = "1678888888";
        String nonce = "randomNonce";

        String signatureStr = timestamp + "\n" + nonce + "\n" + payload + "\n";

        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(keyPair.getPrivate());
        sig.update(signatureStr.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(sig.sign());

        String headers = "Wechatpay-Timestamp: " + timestamp + "\n" +
                "Wechatpay-Nonce: " + nonce + "\n" +
                "Wechatpay-Signature: " + signature + "\n";

        WebhookEvent event = WebhookEvent.builder()
                .headers(headers)
                .payload(tamperedPayload) // Tampered!
                .build();

        Subscription sub = Subscription.builder()
                .verifySecret(publicKeyPem)
                .build();

        Assertions.assertFalse(verifier.verify(event, sub));
    }
}
