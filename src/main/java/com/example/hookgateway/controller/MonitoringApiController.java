package com.example.hookgateway.controller;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.hookgateway.repository.WebhookEventRepository;
import com.example.hookgateway.repository.SubscriptionRepository;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.util.HashMap;
import java.util.Map;

/**
 * 监控数据 API
 */
@RestController
@RequestMapping("/api/monitoring")
public class MonitoringApiController {

    @Autowired
    private HealthEndpoint healthEndpoint;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private WebhookEventRepository webhookEventRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    /**
     * 获取系统概览数据
     */
    @GetMapping("/overview")
    public Map<String, Object> getOverview() {
        Map<String, Object> overview = new HashMap<>();

        // 健康状态
        overview.put("status", healthEndpoint.health().getStatus().getCode());

        // JVM 内存信息
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
        long maxMemory = memoryBean.getHeapMemoryUsage().getMax();
        overview.put("memoryUsed", usedMemory);
        overview.put("memoryMax", maxMemory);
        overview.put("memoryUsedMB", usedMemory / 1024 / 1024);
        overview.put("memoryMaxMB", maxMemory / 1024 / 1024);
        overview.put("memoryUsagePercent", maxMemory > 0 ? (int) ((usedMemory * 100.0) / maxMemory) : 0);

        // CPU 信息
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        overview.put("availableProcessors", osBean.getAvailableProcessors());
        overview.put("systemLoadAverage", osBean.getSystemLoadAverage());

        // 系统运行时间
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
        overview.put("uptimeMs", uptime);
        overview.put("uptimeFormatted", formatUptime(uptime));

        // 业务数据
        long totalEvents = webhookEventRepository.count();
        overview.put("totalEvents", totalEvents);
        overview.put("totalSubscriptions", subscriptionRepository.count());
        long successfulEvents = webhookEventRepository.countByStatus("SUCCESS");
        overview.put("successfulEvents", successfulEvents);
        overview.put("failedEvents", webhookEventRepository.countByStatus("FAILED"));

        // 计算成功率
        double successRate = totalEvents > 0 ? (successfulEvents * 100.0 / totalEvents) : 0;
        overview.put("successRate", String.format("%.1f", successRate));

        return overview;
    }

    /**
     * 获取 HTTP 请求统计
     */
    @GetMapping("/http-stats")
    public Map<String, Object> getHttpStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            // 获取 HTTP 请求总数
            // http.server.requests 通常是一个 Timer
            Timer timer = meterRegistry.find("http.server.requests").timer();
            if (timer != null) {
                stats.put("totalRequests", timer.count());
                stats.put("totalTime", timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS));
                stats.put("maxTime", timer.max(java.util.concurrent.TimeUnit.MILLISECONDS));
                stats.put("meanTime", timer.mean(java.util.concurrent.TimeUnit.MILLISECONDS));
            } else {
                stats.put("totalRequests", 0L);
            }
        } catch (Exception e) {
            stats.put("totalRequests", 0L);
            stats.put("error", e.getMessage());
        }

        return stats;
    }

    /**
     * 格式化运行时间
     */
    private String formatUptime(long uptimeMs) {
        long seconds = uptimeMs / 1000;
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours, minutes);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, secs);
        } else {
            return String.format("%ds", secs);
        }
    }
}
