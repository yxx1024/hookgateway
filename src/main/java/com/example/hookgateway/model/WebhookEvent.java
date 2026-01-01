package com.example.hookgateway.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String source;
    private String method;

    @Column(columnDefinition = "TEXT")
    private String headers;

    @Column(columnDefinition = "TEXT")
    private String payload;

    private LocalDateTime receivedAt;

    // V6: Delivery fields
    @Builder.Default
    private String status = "RECEIVED"; // RECEIVED, SUCCESS, PARTIAL_SUCCESS, FAILED, NO_MATCH

    @Builder.Default
    private Integer deliveryCount = 0;

    @Column(columnDefinition = "TEXT")
    private String deliveryDetails; // JSON or formatted text of delivery results

    private LocalDateTime lastDeliveryAt;
}
