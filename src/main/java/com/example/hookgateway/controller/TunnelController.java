package com.example.hookgateway.controller;

import com.example.hookgateway.websocket.TunnelSessionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * 隧道控制器
 * 提供隧道 Key 管理和状态查询 API
 */
@RestController
@RequestMapping("/api/tunnel")
@RequiredArgsConstructor
public class TunnelController {

    private final TunnelSessionManager sessionManager;

    /**
     * 生成新的隧道 Key
     */
    @PostMapping("/generate-key")
    public ResponseEntity<Map<String, String>> generateKey() {
        String tunnelKey = UUID.randomUUID().toString();
        return ResponseEntity.ok(Map.of("tunnelKey", tunnelKey));
    }

    /**
     * 查询隧道连接状态
     */
    @GetMapping("/status/{tunnelKey}")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable String tunnelKey) {
        boolean connected = sessionManager.isConnected(tunnelKey);
        return ResponseEntity.ok(Map.of(
                "tunnelKey", tunnelKey,
                "connected", connected,
                "status", connected ? "CONNECTED" : "DISCONNECTED"));
    }

    /**
     * 获取活跃连接数统计
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        int activeConnections = sessionManager.getActiveConnectionCount();
        return ResponseEntity.ok(Map.of(
                "activeConnections", activeConnections));
    }
}
