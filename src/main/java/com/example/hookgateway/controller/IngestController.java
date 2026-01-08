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

/**
 * Webhook 摄入控制器。
 */
@RestController
@RequestMapping("/hooks")
@RequiredArgsConstructor
@Slf4j
public class IngestController {

    private final WebhookEventRepository eventRepository;
    private final WebhookProcessingService processingService;

    // Redis 支持（可选）
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @org.springframework.beans.factory.annotation.Value("${app.ingest.mode:sync}")
    private String ingestMode;

    @org.springframework.beans.factory.annotation.Value("${app.ingest.stream.key:webhook:events:ingest}")
    private String ingestStreamKey;

    @org.springframework.beans.factory.annotation.Value("${app.distribution.mode:async}")
    private String distributionMode;

    /**
     * 接收 Webhook 并保存事件，按分发模式投递。
     *
     * @param source  来源标识
     * @param request HTTP 请求
     * @param body    请求体
     * @return 响应结果
     * @throws IOException 读取请求失败
     */
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

        // 1. Redis 异步摄入（写后）
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
                // 失败后继续走同步逻辑
            }
        }

        // 2. 默认同步摄入
        WebhookEvent event = WebhookEvent.builder()
                .source(source)
                .method(request.getMethod())
                .headers(headers.toString())
                .payload(body)
                .receivedAt(LocalDateTime.now())
                .status("PENDING") // 初始状态
                .build();

        final WebhookEvent savedEvent = eventRepository.save(event);

        // 根据分发模式进行异步转发
        if ("redis".equalsIgnoreCase(distributionMode) && redisAvailable) {
            log.info("Dispatching event {} via Redis Stream", savedEvent.getId());
            // 添加消息到流
            redisTemplate.opsForStream().add(
                    com.example.hookgateway.config.RedisStreamConfig.STREAM_KEY,
                    java.util.Collections.singletonMap("eventId", String.valueOf(savedEvent.getId())));
            // 裁剪流长度，保留最近约 10000 条消息（使用近似模式，性能更好）
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
