package com.example.hookgateway.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "subscription")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String source; // 例如 "github"

    @Column(nullable = false)
    private String targetUrl; // 例如 "http://localhost:8081"

    @Builder.Default
    private boolean active = true;

    // V9: 过滤规则支持
    @Builder.Default
    private String filterType = "NONE"; // NONE, JSON_PATH, REGEX

    private String filterRule; // JSONPath 或正则表达式

    // V10: 安全验签支持
    @Builder.Default
    private String verifyMethod = "NONE"; // NONE, HMAC_SHA256

    @Column(columnDefinition = "TEXT")
    private String verifySecret; // HMAC 等验签的密钥

    private String signatureHeader; // 签名所在的请求头名（如 X-Hub-Signature-256）

    // V11: Webhook 隧道支持
    @Builder.Default
    private String destinationType = "HTTP"; // HTTP, TUNNEL

    @Column(length = 100)
    private String tunnelKey; // 隧道认证密钥（UUID 格式）

    @CreationTimestamp
    private LocalDateTime createdAt;
}
