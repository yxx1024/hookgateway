package com.example.hookgateway.controller;

import com.example.hookgateway.model.WebhookEvent;
import com.example.hookgateway.repository.WebhookEventRepository;
import com.example.hookgateway.service.WebhookProcessingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Enumeration;

@RestController
@RequestMapping("/hooks")
@RequiredArgsConstructor
@Slf4j
public class IngestController {

    private final WebhookEventRepository eventRepository;
    private final WebhookProcessingService processingService;

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
            processingService.processEventAsync(savedEvent);
        }

        return org.springframework.http.ResponseEntity.ok("Received");
    }

}
