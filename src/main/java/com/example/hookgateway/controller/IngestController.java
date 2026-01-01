package com.example.hookgateway.controller;

import com.example.hookgateway.model.Subscription;
import com.example.hookgateway.model.WebhookEvent;
import com.example.hookgateway.repository.SubscriptionRepository;
import com.example.hookgateway.repository.WebhookEventRepository;
import com.example.hookgateway.service.ReplayService;
import com.example.hookgateway.service.ReplayService.ReplayResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/hooks")
@RequiredArgsConstructor
@Slf4j
public class IngestController {

    private final WebhookEventRepository eventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ReplayService replayService;
    private final com.example.hookgateway.security.VerifierFactory verifierFactory;

    // V11: Tunnel support
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.example.hookgateway.websocket.TunnelSessionManager tunnelSessionManager;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    // Redis support - optional
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @org.springframework.beans.factory.annotation.Value("${app.ingest.mode:sync}")
    private String ingestMode;

    @org.springframework.beans.factory.annotation.Value("${app.ingest.stream.key:webhook:events:ingest}")
    private String ingestStreamKey;

    @org.springframework.beans.factory.annotation.Value("${app.distribution.mode:async}")
    private String distributionMode;

    @PostMapping("/{source}/**")
    public org.springframework.http.ResponseEntity<String> ingest(@PathVariable String source,
            HttpServletRequest request,
            @RequestBody(required = false) String body) throws IOException {
        StringBuilder headers = new StringBuilder();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            headers.append(name).append(": ").append(request.getHeader(name)).append("\n");
        }

        // 1. Redis Async Ingestion (Write-Behind)
        boolean redisAvailable = (redisTemplate != null);
        if ("redis".equalsIgnoreCase(ingestMode) && redisAvailable) {
            try {
                java.util.Map<String, String> eventMap = new java.util.HashMap<>();
                eventMap.put("source", source);
                eventMap.put("method", request.getMethod());
                eventMap.put("headers", headers.toString());
                eventMap.put("payload", body == null ? "" : body);
                eventMap.put("receivedAt", LocalDateTime.now().toString());

                redisTemplate.opsForStream().add(ingestStreamKey, eventMap);

                return org.springframework.http.ResponseEntity.accepted().body("Accepted");
            } catch (Exception e) {
                log.error("Failed to push to Redis Ingest Stream, falling back to Sync", e);
                // Fallback will continue below
            }
        }

        // 2. Default Sync Ingestion
        WebhookEvent event = WebhookEvent.builder()
                .source(source)
                .method(request.getMethod())
                .headers(headers.toString())
                .payload(body)
                .receivedAt(LocalDateTime.now())
                .status("PENDING") // Initial status for tracking
                .build();

        final WebhookEvent savedEvent = eventRepository.save(event);

        // Async Forwarding based on mode (Distribution Mode)
        if ("redis".equalsIgnoreCase(distributionMode) && redisAvailable) {
            log.info("Dispatching event {} via Redis Stream", savedEvent.getId());
            // 添加消息到 Stream
            redisTemplate.opsForStream().add(
                    com.example.hookgateway.config.RedisStreamConfig.STREAM_KEY,
                    java.util.Collections.singletonMap("eventId", String.valueOf(savedEvent.getId())));
            // 裁剪 Stream 长度，保留最近约 10000 条消息（使用近似模式，性能更好）
            redisTemplate.opsForStream().trim(
                    com.example.hookgateway.config.RedisStreamConfig.STREAM_KEY, 10000, true);
        } else {
            if ("redis".equalsIgnoreCase(distributionMode)) {
                log.warn("Mode is 'redis' but RedisTemplate is null. Fallback to Local @Async.");
            }
            log.info("Dispatching event {} via Local @Async", savedEvent.getId());
            triggerAutoForward(savedEvent);
        }

        return org.springframework.http.ResponseEntity.ok("Received");
    }

    @Async
    protected void triggerAutoForward(WebhookEvent event) {
        processEvent(event);
    }

    /**
     * Core processing logic, extracted for reuse by ReplayController or Redis
     * Consumer
     */
    public void processEvent(WebhookEvent event) {
        List<Subscription> subs = subscriptionRepository.findBySourceAndActiveTrue(event.getSource());

        if (subs.isEmpty()) {
            event.setStatus("NO_MATCH");
            eventRepository.save(event);
            return;
        }

        StringBuilder report = new StringBuilder();
        int successCount = 0;

        for (Subscription sub : subs) {
            // V10: Security Verification Logic
            boolean isVerified = true;
            String verificationLog = "";

            if (!"NONE".equalsIgnoreCase(sub.getVerifyMethod()) && sub.getVerifySecret() != null) {
                com.example.hookgateway.security.VerifierStrategy strategy = verifierFactory
                        .getStrategy(sub.getVerifyMethod());

                if (strategy != null) {
                    try {
                        // Strategy now handles header extraction (HMAC uses sub.signatureHeader, WeChat
                        // uses standard headers)
                        boolean valid = strategy.verify(event, sub);
                        if (!valid) {
                            isVerified = false;
                            verificationLog = "Verification Failed: Invalid Signature or Request";
                        }
                    } catch (Exception e) {
                        isVerified = false;
                        verificationLog = "Verification Error: " + e.getMessage();
                        log.error("Verification exception for subscription {}", sub.getId(), e);
                    }
                }
            }

            if (!isVerified) {
                report.append("--- Verification Failed for ").append(sub.getTargetUrl()).append(" ---\n")
                        .append(verificationLog).append("\n\n");
                continue;
            }

            // V9: Filter Logic
            boolean shouldSend = true;
            String filterLog = "";

            if ("JSON_PATH".equals(sub.getFilterType()) && sub.getFilterRule() != null
                    && !sub.getFilterRule().isEmpty()) {
                try {
                    com.jayway.jsonpath.DocumentContext jsonContext = com.jayway.jsonpath.JsonPath
                            .parse(event.getPayload());
                    // Support boolean expressions like $.type == 'push'
                    // JsonPath.parse(json).read("$.type == 'push'", List.class) returns filtered
                    // objects.
                    // But here we want a condition.
                    // Actually, Jayway JsonPath is for extraction. For filtering, we might need
                    // Predicates.
                    // But simple usage: check if extraction is not empty or if value matches.
                    // User might enter: $.store.book[?(@.price < 10)]
                    // Let's assume User enters a Path that should return NON-EMPTY result if match.

                    // Actually, let's use a simpler approach for MVP:
                    // If the rule is a Predicate (starts with ?), treat as filter.
                    // If standard path, check if exists.

                    // Wait, user wants $.type == 'push'. Standard JsonPath: $[?(@.type == 'push')]
                    // So if user enters $[?(@.type == 'push')], and result is not empty List, then
                    // match.

                    Object result = jsonContext.read(sub.getFilterRule());
                    if (result instanceof List && ((List<?>) result).isEmpty()) {
                        shouldSend = false;
                        filterLog = "Skipped by JSONPath filter: No match for " + sub.getFilterRule();
                    } else if (result == null) {
                        shouldSend = false;
                        filterLog = "Skipped by JSONPath filter: Result null";
                    }

                    // Note: If user enters just $.type, and it exists, it sends.
                } catch (Exception e) {
                    shouldSend = false;
                    filterLog = "Skipped by JSONPath filter: Parsing error (" + e.getMessage() + ")";
                }
            } else if ("REGEX".equals(sub.getFilterType()) && sub.getFilterRule() != null
                    && !sub.getFilterRule().isEmpty()) {
                try {
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(sub.getFilterRule());
                    if (!pattern.matcher(event.getPayload()).find()) {
                        shouldSend = false;
                        filterLog = "Skipped by Regex filter: No match for " + sub.getFilterRule();
                    }
                } catch (Exception e) {
                    shouldSend = false;
                    filterLog = "Skipped by Regex filter: Invalid pattern (" + e.getMessage() + ")";
                }
            }

            if (!shouldSend) {
                report.append("--- Filtered for ").append(sub.getTargetUrl()).append(" ---\n")
                        .append(filterLog).append("\n\n");
                continue;
            }

            // V11: Tunnel Routing Logic
            if ("TUNNEL".equalsIgnoreCase(sub.getDestinationType())) {
                // 现在通过 SessionManager 进行路由（支持本地路由和分布式广播）
                String deliveryLog = tunnelSessionManager.routeEvent(event, sub.getTunnelKey());
                report.append("--- Delivery Report for TUNNEL (").append(sub.getTunnelKey()).append(") ---\n")
                        .append(deliveryLog).append("\n\n");
                continue;
            }

            // V8: Clear previous logs before starting a new subscription delivery
            replayService.startNewTracking();

            ReplayResult result = replayService.replayWithRetry(
                    event.getMethod(),
                    event.getHeaders(),
                    event.getPayload(),
                    sub.getTargetUrl());

            if (result.isSuccess())
                successCount++;

            // Use the detailed log from ReplayResult
            report.append("--- Delivery Report for ").append(sub.getTargetUrl()).append(" ---\n")
                    .append(result.getLog()).append("\n\n");
        }

        // Update overall status
        if (successCount == subs.size()) {
            event.setStatus("SUCCESS");
        } else if (successCount > 0) {
            event.setStatus("PARTIAL_SUCCESS");
        } else {
            event.setStatus("FAILED");
        }

        event.setDeliveryCount(subs.size());
        event.setDeliveryDetails(report.toString());
        event.setLastDeliveryAt(LocalDateTime.now());
        eventRepository.save(event);

        log.info("Event {} processed: {}/{} success", event.getId(), successCount, subs.size());
    }

}
