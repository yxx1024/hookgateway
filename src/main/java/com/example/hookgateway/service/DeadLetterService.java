package com.example.hookgateway.service;

import com.example.hookgateway.config.RedisStreamConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 死信队列服务：处理反复失败、超过最大重试次数的消息。
 * 超过阈值后移入死信队列（DLQ），并从主流中确认（ACK）。
 */
@Service
@Slf4j
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.distribution.mode", havingValue = "redis")
public class DeadLetterService {

    private final StringRedisTemplate redisTemplate;

    public static final String DLQ_STREAM_KEY = "webhook:dlq";
    public static final int MAX_DELIVERY_COUNT = 5;

    /**
     * 检查消息是否应移至死信队列
     * 
     * @param deliveryCount 当前投递次数
     * @return true 如果超过阈值
     */
    public boolean shouldMoveToDeadLetter(long deliveryCount) {
        return deliveryCount >= MAX_DELIVERY_COUNT;
    }

    /**
     * 将消息移至死信队列
     * 
     * @param originalMessage 原始消息
     * @param errorReason     失败原因
     */
    public void moveToDeadLetter(MapRecord<String, String, String> originalMessage, String errorReason) {
        try {
            Map<String, String> dlqPayload = new java.util.HashMap<>(originalMessage.getValue());
            dlqPayload.put("_originalId", originalMessage.getId().getValue());
            dlqPayload.put("_errorReason", errorReason);
            dlqPayload.put("_movedAt", java.time.Instant.now().toString());

            // 写入 DLQ
            RecordId dlqId = redisTemplate.opsForStream().add(DLQ_STREAM_KEY, dlqPayload);
            log.warn("Message moved to DLQ: originalId={}, dlqId={}, reason={}",
                    originalMessage.getId(), dlqId, errorReason);

            // ACK 原消息，避免它继续被重试
            redisTemplate.opsForStream().acknowledge(
                    RedisStreamConfig.STREAM_KEY,
                    RedisStreamConfig.GROUP_NAME,
                    originalMessage.getId());

        } catch (Exception e) {
            log.error("Failed to move message to DLQ: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取死信队列中的消息数量
     */
    public long getDeadLetterCount() {
        Long size = redisTemplate.opsForStream().size(DLQ_STREAM_KEY);
        return size != null ? size : 0;
    }
}
