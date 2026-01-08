package com.example.hookgateway.controller;

import com.example.hookgateway.model.CleanupConfig;
import com.example.hookgateway.service.CleanupSchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 清理配置接口控制器。
 */
@RestController
@RequestMapping("/api/cleanup")
@RequiredArgsConstructor
public class CleanupConfigController {

    private final CleanupSchedulerService cleanupService;

    /**
     * 获取当前清理配置
     */
    @GetMapping("/config")
    public ResponseEntity<CleanupConfig> getConfig() {
        return ResponseEntity.ok(cleanupService.getConfig());
    }

    /**
     * 更新清理配置
     */
    @PutMapping("/config")
    public ResponseEntity<CleanupConfig> updateConfig(@RequestBody Map<String, Object> request) {
        Integer retentionDays = (Integer) request.get("retentionDays");
        Boolean enabled = (Boolean) request.get("enabled");

        if (retentionDays == null || enabled == null) {
            return ResponseEntity.badRequest().build();
        }

        // 验证 retentionDays 只能是 7/10/15/30
        if (retentionDays != 7 && retentionDays != 10 && retentionDays != 15 && retentionDays != 30) {
            return ResponseEntity.badRequest().build();
        }

        CleanupConfig updated = cleanupService.updateConfig(retentionDays, enabled);
        return ResponseEntity.ok(updated);
    }

    /**
     * 手动触发清理（用于测试）
     */
    @PostMapping("/run")
    public ResponseEntity<CleanupSchedulerService.CleanupResult> runCleanup() {
        CleanupSchedulerService.CleanupResult result = cleanupService.runCleanup();
        return ResponseEntity.ok(result);
    }
}
