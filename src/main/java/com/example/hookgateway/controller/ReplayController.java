package com.example.hookgateway.controller;

import com.example.hookgateway.service.ReplayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 重放接口控制器。
 */
@RestController
@RequestMapping("/api/replay")
@RequiredArgsConstructor
public class ReplayController {

    private final ReplayService replayService;
    private final com.example.hookgateway.repository.WebhookEventRepository eventRepository;
    private final com.example.hookgateway.websocket.TunnelSessionManager tunnelSessionManager;

    /**
     * 重放指定事件，可选择目标 URL 或隧道 Key。
     *
     * @param id        事件 ID
     * @param targetUrl 目标 URL
     * @param tunnelKey 隧道 Key
     * @return 重放结果
     */
    @PostMapping("/{id}")
    public ResponseEntity<String> replay(
            @PathVariable Long id, 
            @RequestParam(required = false) String targetUrl,
            @RequestParam(required = false) String tunnelKey) {
        
        return eventRepository.findById(id).map(event -> {
            String resultMsg;
            boolean isSuccess;
            int responseStatusCode = 200;

            if (tunnelKey != null && !tunnelKey.trim().isEmpty()) {
                // 隧道重放
                String deliveryLog = tunnelSessionManager.routeEvent(event, tunnelKey);
                isSuccess = deliveryLog.startsWith("SUCCESS");
                resultMsg = "Tunnel Replay (" + tunnelKey + "): " + deliveryLog;
            } else if (targetUrl != null && !targetUrl.trim().isEmpty()) {
                // HTTP URL 重放（带重试，V12）
                replayService.startNewTracking();
                ReplayService.ReplayResult result = replayService.replayWithRetry(event.getMethod(),
                        event.getHeaders(), event.getPayload(), targetUrl);
                
                isSuccess = result.isSuccess();
                responseStatusCode = result.getStatusCode();
                resultMsg = "Manual Replay to " + targetUrl + ": " + (isSuccess ? " (SUCCESS)" : " (FAILED)");
                // 此时 resultMsg 只是摘要，详细轨迹在 result.getLog() 中
                resultMsg += "\n" + result.getLog();
            } else {
                return ResponseEntity.badRequest().body("Either targetUrl or tunnelKey must be provided");
            }

            // 将日志追加到 deliveryDetails
            String timestamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String newLog = String.format("\n[%s] %s", timestamp, resultMsg);

            String currentDetails = event.getDeliveryDetails();
            event.setDeliveryDetails(currentDetails == null ? newLog : currentDetails + newLog);
            event.setLastDeliveryAt(java.time.LocalDateTime.now());

            // 根据最后一次操作结果更新状态（V12.1：细化状态机）
            String currentStatus = event.getStatus();
            if (isSuccess) {
                event.setStatus("SUCCESS");
            } else {
                // 手动重放失败但历史成功时，标记为 PARTIAL_SUCCESS，保留历史信息
                if ("SUCCESS".equals(currentStatus)) {
                    event.setStatus("PARTIAL_SUCCESS");
                } else if (currentStatus == null || "RECEIVED".equals(currentStatus) || "PENDING".equals(currentStatus)) {
                    event.setStatus("FAILED");
                }
                // 保持 PARTIAL_SUCCESS、FAILED、NO_MATCH 不变
            }

            eventRepository.save(event);

            if (isSuccess) {
                return ResponseEntity.ok("Success: " + resultMsg);
            } else {
                int status = responseStatusCode > 0 ? responseStatusCode : 500;
                return ResponseEntity.status(status).body("Failed: " + resultMsg);
            }
        }).orElse(ResponseEntity.notFound().build());
    }
}
