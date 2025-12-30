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

    @PostMapping("/{id}")
    public ResponseEntity<String> replay(@PathVariable Long id, @RequestParam String targetUrl) {
        return eventRepository.findById(id).map(event -> {
            ReplayService.ReplayResult result = replayService.replay(event.getMethod(),
                    event.getHeaders(), event.getPayload(), targetUrl);

            // Append log to deliveryDetails
            String timestamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String newLog = String.format("\n[%s] Manual Replay to %s: HTTP %d %s",
                    timestamp, targetUrl, result.getStatusCode(), result.isSuccess() ? "(SUCCESS)" : "(FAILED)");

            String currentDetails = event.getDeliveryDetails();
            event.setDeliveryDetails(currentDetails == null ? newLog : currentDetails + newLog);
            event.setLastDeliveryAt(java.time.LocalDateTime.now());

            // Update status if successful
            if (result.isSuccess()) {
                event.setStatus("SUCCESS");
            }
            // If failed, we don't necessarily want to overwrite a previous success, so we
            // keep as is or only update if it was FAILED?
            // User requested: "If success, update". Implies if failed, maybe no change or
            // just log.
            // Let's stick to updating to SUCCESS if success.

            eventRepository.save(event);

            if (result.isSuccess()) {
                return ResponseEntity.ok("Success: " + result.getMessage());
            } else {
                // Handle cases where statusCode might be -1 or 0
                int status = result.getStatusCode() > 0 ? result.getStatusCode() : 500;
                return ResponseEntity.status(status).body("Failed: " + result.getMessage());
            }
        }).orElse(ResponseEntity.notFound().build());
    }
}
