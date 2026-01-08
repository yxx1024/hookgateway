package com.example.hookgateway.service;

import com.example.hookgateway.model.WebhookEvent;
import com.example.hookgateway.repository.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.ingest.mode", havingValue = "redis")
public class EventPersister {

    private final StringRedisTemplate redisTemplate;
    private final WebhookEventRepository eventRepository;
    private final WebhookProcessingService processingService;

    @Value("${app.ingest.stream.key:webhook:events:ingest}")
    private String ingestStreamKey;

    @Value("${app.distribution.mode:async}")
    private String distributionMode;

    private static final int BATCH_SIZE = 500;

    @Scheduled(fixedDelay = 100) // 每 100ms 轮询一次
    @SuppressWarnings("unchecked")
    public void processPendingEvents() {
        try {
            // 使用消费者组从 Redis 流批量拉取并 ACK，重启更安全
            // 需要先确保消费者组存在

            String group = "webhook-ingest-group";
            String consumer = "persister-" + java.net.InetAddress.getLocalHost().getHostName();

            try {
                redisTemplate.opsForStream().createGroup(ingestStreamKey, group);
            } catch (Exception e) {
                // 消费者组已存在，忽略即可
            }

            List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream().read(
                    org.springframework.data.redis.connection.stream.Consumer.from(group, consumer),
                    org.springframework.data.redis.connection.stream.StreamReadOptions.empty().count(BATCH_SIZE),
                    StreamOffset.create(ingestStreamKey, ReadOffset.lastConsumed()));

            if (messages == null || messages.isEmpty()) {
                return;
            }

            List<WebhookEvent> eventsToSave = new ArrayList<>();
            List<String> recordIds = new ArrayList<>();

            for (MapRecord<String, Object, Object> message : messages) {
                Map<Object, Object> body = message.getValue();
                WebhookEvent event = WebhookEvent.builder()
                        .source((String) body.get("source"))
                        .method((String) body.get("method"))
                        .headers((String) body.get("headers"))
                        .payload((String) body.get("payload"))
                        .receivedAt(LocalDateTime.parse((String) body.get("receivedAt"))) // 按 ISO 时间解析
                        .status("PENDING")
                        .build();
                eventsToSave.add(event);
                recordIds.add(message.getId().getValue());
            }

            // 批量入库
            List<WebhookEvent> savedEvents = eventRepository.saveAll(eventsToSave);
            log.info("[Persister] Flushed {} events to DB", savedEvents.size());

            // 入库后继续分发逻辑
            if ("redis".equalsIgnoreCase(distributionMode)) {
                // 推送到分发流
                for (WebhookEvent saved : savedEvents) {
                    log.info("Dispatching event {} via Redis Stream (from Persister)", saved.getId());
                    redisTemplate.opsForStream().add(
                            com.example.hookgateway.config.RedisStreamConfig.STREAM_KEY,
                            java.util.Collections.singletonMap("eventId", String.valueOf(saved.getId())));
                }
            } else {
                for (WebhookEvent saved : savedEvents) {
                    processingService.processEventAsync(saved);
                }
            }

            // 确认（ACK）
            redisTemplate.opsForStream().acknowledge(ingestStreamKey, group, recordIds.toArray(new String[0]));

            // 可选：删除已处理记录以释放内存
            redisTemplate.opsForStream().delete(ingestStreamKey, recordIds.toArray(new String[0]));

        } catch (Exception e) {
            log.error("Error in EventPersister", e);
        }
    }
}
