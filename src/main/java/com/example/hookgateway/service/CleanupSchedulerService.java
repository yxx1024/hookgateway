package com.example.hookgateway.service;

import com.example.hookgateway.model.CleanupConfig;
import com.example.hookgateway.repository.CleanupConfigRepository;
import com.example.hookgateway.repository.WebhookEventRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class CleanupSchedulerService {

    private final CleanupConfigRepository configRepository;
    private final WebhookEventRepository eventRepository;

    /**
     * 初始化默认配置
     */
    @PostConstruct
    public void initDefaultConfig() {
        if (configRepository.count() == 0) {
            CleanupConfig defaultConfig = CleanupConfig.builder()
                    .retentionDays(30)
                    .enabled(false)
                    .updatedAt(LocalDateTime.now())
                    .build();
            configRepository.save(defaultConfig);
            log.info("Initialized default cleanup config: retentionDays=30, enabled=false");
        }
    }

    /**
     * 定时任务：每天凌晨 2 点执行清理
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void scheduledCleanup() {
        log.info("Scheduled cleanup task triggered");
        runCleanup();
    }

    /**
     * 手动触发清理
     */
    @Transactional
    public CleanupResult runCleanup() {
        CleanupConfig config = getConfig();

        if (!config.getEnabled()) {
            log.info("Cleanup is disabled, skipping");
            return CleanupResult.builder()
                    .executed(false)
                    .message("Cleanup is disabled")
                    .build();
        }

        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(config.getRetentionDays());
        log.info("Starting cleanup: deleting events older than {} (retention: {} days)",
                cutoffDate, config.getRetentionDays());

        int deletedCount = eventRepository.deleteByReceivedAtBefore(cutoffDate);

        config.setLastRunAt(LocalDateTime.now());
        config.setLastCleanupCount((long) deletedCount);
        configRepository.save(config);

        log.info("Cleanup completed: deleted {} events", deletedCount);

        return CleanupResult.builder()
                .executed(true)
                .deletedCount((long) deletedCount)
                .message("Successfully deleted " + deletedCount + " events")
                .build();
    }

    /**
     * 获取当前配置（如果不存在则创建默认配置）
     */
    public CleanupConfig getConfig() {
        return configRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> {
                    CleanupConfig newConfig = CleanupConfig.builder()
                            .retentionDays(30)
                            .enabled(false)
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return configRepository.save(newConfig);
                });
    }

    /**
     * 更新配置
     */
    @Transactional
    public CleanupConfig updateConfig(Integer retentionDays, Boolean enabled) {
        CleanupConfig config = getConfig();
        config.setRetentionDays(retentionDays);
        config.setEnabled(enabled);
        config.setUpdatedAt(LocalDateTime.now());
        return configRepository.save(config);
    }

    public record CleanupResult(boolean executed, Long deletedCount, String message) {
        public static CleanupResultBuilder builder() {
            return new CleanupResultBuilder();
        }

        public static class CleanupResultBuilder {
            private boolean executed;
            private Long deletedCount;
            private String message;

            public CleanupResultBuilder executed(boolean executed) {
                this.executed = executed;
                return this;
            }

            public CleanupResultBuilder deletedCount(Long deletedCount) {
                this.deletedCount = deletedCount;
                return this;
            }

            public CleanupResultBuilder message(String message) {
                this.message = message;
                return this;
            }

            public CleanupResult build() {
                return new CleanupResult(executed, deletedCount, message);
            }
        }
    }
}
