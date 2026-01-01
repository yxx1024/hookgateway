package com.example.hookgateway.controller;

import com.example.hookgateway.service.ReplayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/replay")
@RequiredArgsConstructor
public class ReplayController {

    private final ReplayService replayService;
    private final com.example.hookgateway.repository.WebhookEventRepository eventRepository;
    private final com.example.hookgateway.websocket.TunnelSessionManager tunnelSessionManager;

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
                // Tunnel Replay
                String deliveryLog = tunnelSessionManager.routeEvent(event, tunnelKey);
                isSuccess = deliveryLog.startsWith("SUCCESS");
                resultMsg = "Tunnel Replay (" + tunnelKey + "): " + deliveryLog;
            } else if (targetUrl != null && !targetUrl.trim().isEmpty()) {
                // HTTP URL Replay with Retry Support (V12)
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

            // Append log to deliveryDetails
            String timestamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String newLog = String.format("\n[%s] %s", timestamp, resultMsg);

            String currentDetails = event.getDeliveryDetails();
            event.setDeliveryDetails(currentDetails == null ? newLog : currentDetails + newLog);
            event.setLastDeliveryAt(java.time.LocalDateTime.now());

            // Update status based on last action result (V12.1: Nuanced State Machine)
            String currentStatus = event.getStatus();
            if (isSuccess) {
                event.setStatus("SUCCESS");
            } else {
                // If manual replay failed but it was previously successful, 
                // mark as PARTIAL_SUCCESS to indicate a regression/issue without losing history.
                if ("SUCCESS".equals(currentStatus)) {
                    event.setStatus("PARTIAL_SUCCESS");
                } else if (currentStatus == null || "RECEIVED".equals(currentStatus) || "PENDING".equals(currentStatus)) {
                    event.setStatus("FAILED");
                }
                // Maintain PARTIAL_SUCCESS, FAILED or NO_MATCH as is
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
