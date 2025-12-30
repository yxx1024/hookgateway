package com.example.hookgateway.service;

import com.example.hookgateway.config.RedisStreamConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 定时任务：恢复 Pending List 中长时间未确认的消息。
 * 当消费者宕机或处理失败时，消息会留在 Pending List 中。
 * 此任务会 XCLAIM 这些消息并重新投递给当前消费者处理。
 */
@Component
@Slf4j
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.distribution.mode", havingValue = "redis")
public class PendingMessageRecoveryTask {

    private final StringRedisTemplate redisTemplate;
    private final WebhookStreamConsumer consumer;
    private final DeadLetterService deadLetterService;

    // 消息空闲超过此时间视为需要恢复（毫秒）
    private static final long PENDING_IDLE_TIME_MS = 60_000; // 1 分钟

    // 每次最多恢复多少条消息
    private static final int MAX_RECOVER_COUNT = 100;

    /**
     * 每 30 秒执行一次 Pending 恢复检查
     */
    @Scheduled(fixedDelay = 30_000)
    public void recoverPendingMessages() {
        try {
            // 1. 获取 Pending 消息摘要
            PendingMessagesSummary summary = redisTemplate.opsForStream().pending(
                    RedisStreamConfig.STREAM_KEY,
                    RedisStreamConfig.GROUP_NAME);

            if (summary == null || summary.getTotalPendingMessages() == 0) {
                return;
            }

            log.info("Found {} pending messages in group {}",
                    summary.getTotalPendingMessages(), RedisStreamConfig.GROUP_NAME);

            // 2. 获取详细的 Pending 消息列表
            PendingMessages pendingMessages = redisTemplate.opsForStream().pending(
                    RedisStreamConfig.STREAM_KEY,
                    Consumer.from(RedisStreamConfig.GROUP_NAME, RedisStreamConfig.CONSUMER_NAME),
                    Range.unbounded(),
                    MAX_RECOVER_COUNT);

            for (PendingMessage pm : pendingMessages) {
                // 3. 仅处理空闲时间超过阈值的消息
                // 使用 getElapsedTimeSinceLastDelivery() 获取空闲时间
                Duration idleTime = pm.getElapsedTimeSinceLastDelivery();
                long idleTimeMs = idleTime.toMillis();

                if (idleTimeMs > PENDING_IDLE_TIME_MS) {
                    log.info("Recovering pending message: id={}, idleTime={}ms, deliveryCount={}",
                            pm.getId(), idleTimeMs, pm.getTotalDeliveryCount());

                    // 4. XCLAIM 消息到当前消费者
                    List<MapRecord<String, Object, Object>> claimed = redisTemplate.opsForStream().claim(
                            RedisStreamConfig.STREAM_KEY,
                            RedisStreamConfig.GROUP_NAME,
                            RedisStreamConfig.CONSUMER_NAME,
                            Duration.ofMillis(PENDING_IDLE_TIME_MS),
                            pm.getId());

                    // 5. 检查是否超过最大重试次数
                    for (MapRecord<String, Object, Object> record : claimed) {
                        // 转换为 String 类型的 MapRecord（通过 toString 显式转换，无需 suppress）
                        java.util.Map<String, String> stringMap = record.getValue().entrySet().stream()
                                .collect(java.util.stream.Collectors.toMap(
                                        e -> e.getKey().toString(),
                                        e -> e.getValue() != null ? e.getValue().toString() : ""));
                        MapRecord<String, String, String> stringRecord = MapRecord.create(record.getStream(), stringMap)
                                .withId(record.getId());

                        // 检查是否应移至死信队列
                        if (deadLetterService.shouldMoveToDeadLetter(pm.getTotalDeliveryCount())) {
                            log.warn("Message exceeded max delivery count, moving to DLQ: id={}", pm.getId());
                            deadLetterService.moveToDeadLetter(stringRecord,
                                    "Exceeded max delivery count: " + pm.getTotalDeliveryCount());
                        } else {
                            // 正常重试
                            consumer.onMessage(stringRecord);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error during pending message recovery: {}", e.getMessage(), e);
        }
    }
}
