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
    private String source; // e.g. "github"

    @Column(nullable = false)
    private String targetUrl; // e.g. "http://localhost:8081"

    @Builder.Default
    private boolean active = true;

    // V9: Filter Support
    @Builder.Default
    private String filterType = "NONE"; // NONE, JSON_PATH, REGEX

    private String filterRule; // JSONPath or Regex pattern

    // V10: Security Verification Support
    @Builder.Default
    private String verifyMethod = "NONE"; // NONE, HMAC_SHA256

    @Column(columnDefinition = "TEXT")
    private String verifySecret; // Secret key for HMAC or other methods

    private String signatureHeader; // Header name containing the signature (e.g., X-Hub-Signature-256)

    // V11: Webhook Tunneling Support
    @Builder.Default
    private String destinationType = "HTTP"; // HTTP, TUNNEL

    @Column(length = 100)
    private String tunnelKey; // Tunnel authentication key (UUID format)

    @CreationTimestamp
    private LocalDateTime createdAt;
}
