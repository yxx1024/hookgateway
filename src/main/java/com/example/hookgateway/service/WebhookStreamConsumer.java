package com.example.hookgateway.service;

import com.example.hookgateway.model.WebhookEvent;
import com.example.hookgateway.repository.WebhookEventRepository;
import com.example.hookgateway.config.RedisStreamConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.distribution.mode", havingValue = "redis")
public class WebhookStreamConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private final WebhookEventRepository eventRepository;
    private final WebhookProcessingService processingService;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        try {
            String eventIdStr = message.getValue().get("eventId");
            if (eventIdStr == null) {
                // 无效消息，直接 ACK 丢弃
                acknowledge(message);
                return;
            }

            Long eventId = Long.parseLong(eventIdStr);
            log.info("Received message from Redis Stream: eventId={}", eventId);

            Optional<WebhookEvent> eventOpt = eventRepository.findById(eventId);
            if (eventOpt.isPresent()) {
                processingService.processEvent(eventOpt.get());
            } else {
                log.warn("Event not found in database: eventId={}", eventId);
            }

            // 处理成功，确认消息
            acknowledge(message);

        } catch (Exception e) {
            log.error("Error processing Redis Stream message: {}. Message will remain in pending list for retry.",
                    e.getMessage(), e);
            // 不 ACK，消息保留在 Pending List 中等待恢复任务重试
        }
    }

    private void acknowledge(MapRecord<String, String, String> message) {
        redisTemplate.opsForStream().acknowledge(
                RedisStreamConfig.STREAM_KEY,
                RedisStreamConfig.GROUP_NAME,
                message.getId());
        log.debug("Message acknowledged: {}", message.getId());
    }
}
