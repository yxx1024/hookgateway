package com.example.hookgateway.service;

import com.example.hookgateway.model.Subscription;
import com.example.hookgateway.model.WebhookEvent;
import com.example.hookgateway.repository.SubscriptionRepository;
import com.example.hookgateway.repository.WebhookEventRepository;
import com.example.hookgateway.security.VerifierFactory;
import com.example.hookgateway.security.VerifierStrategy;
import com.example.hookgateway.websocket.TunnelSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Webhook 处理服务：负责验签、过滤与投递。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookProcessingService {

    private final WebhookEventRepository eventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ReplayService replayService;
    private final VerifierFactory verifierFactory;
    private final TunnelSessionManager tunnelSessionManager;

    @Async("taskExecutor")
    public void processEventAsync(WebhookEvent event) {
        processEvent(event);
    }

    public void processEvent(WebhookEvent event) {
        List<Subscription> subs = subscriptionRepository.findBySourceAndActiveTrue(event.getSource());

        if (subs.isEmpty()) {
            event.setStatus("NO_MATCH");
            eventRepository.save(event);
            return;
        }

        StringBuilder report = new StringBuilder();
        int successCount = 0;

        for (Subscription sub : subs) {
            boolean isVerified = true;
            String verificationLog = "";

            if (!"NONE".equalsIgnoreCase(sub.getVerifyMethod()) && sub.getVerifySecret() != null) {
                VerifierStrategy strategy = verifierFactory.getStrategy(sub.getVerifyMethod());

                if (strategy != null) {
                    try {
                        boolean valid = strategy.verify(event, sub);
                        if (!valid) {
                            isVerified = false;
                            verificationLog = "Verification Failed: Invalid Signature or Request";
                        }
                    } catch (Exception e) {
                        isVerified = false;
                        verificationLog = "Verification Error: " + e.getMessage();
                        log.error("Verification exception for subscription {}", sub.getId(), e);
                    }
                }
            }

            if (!isVerified) {
                report.append("--- Verification Failed for ").append(sub.getTargetUrl()).append(" ---\n")
                        .append(verificationLog).append("\n\n");
                continue;
            }

            boolean shouldSend = true;
            String filterLog = "";

            if ("JSON_PATH".equals(sub.getFilterType()) && sub.getFilterRule() != null
                    && !sub.getFilterRule().isEmpty()) {
                try {
                    com.jayway.jsonpath.DocumentContext jsonContext = com.jayway.jsonpath.JsonPath
                            .parse(event.getPayload());

                    Object result = jsonContext.read(sub.getFilterRule());
                    if (result instanceof List && ((List<?>) result).isEmpty()) {
                        shouldSend = false;
                        filterLog = "Skipped by JSONPath filter: No match for " + sub.getFilterRule();
                    } else if (result == null) {
                        shouldSend = false;
                        filterLog = "Skipped by JSONPath filter: Result null";
                    }
                } catch (Exception e) {
                    shouldSend = false;
                    filterLog = "Skipped by JSONPath filter: Parsing error (" + e.getMessage() + ")";
                }
            } else if ("REGEX".equals(sub.getFilterType()) && sub.getFilterRule() != null
                    && !sub.getFilterRule().isEmpty()) {
                try {
                    com.google.re2j.Pattern pattern = com.google.re2j.Pattern.compile(sub.getFilterRule());
                    boolean found = pattern.matcher(event.getPayload()).find();

                    if (!found) {
                        shouldSend = false;
                        filterLog = "Skipped by Regex filter: No match for " + sub.getFilterRule();
                    }
                } catch (Exception e) {
                    shouldSend = false;
                    filterLog = "Skipped by Regex filter: Error (" + e.getMessage() + ")";
                }
            }

            if (!shouldSend) {
                report.append("--- Filtered for ").append(sub.getTargetUrl()).append(" ---\n")
                        .append(filterLog).append("\n\n");
                continue;
            }

            if ("TUNNEL".equalsIgnoreCase(sub.getDestinationType())) {
                String deliveryLog = tunnelSessionManager.routeEvent(event, sub.getTunnelKey());
                report.append("--- Delivery Report for TUNNEL (").append(sub.getTunnelKey()).append(") ---\n")
                        .append(deliveryLog).append("\n\n");
                continue;
            }

            replayService.startNewTracking();

            ReplayService.ReplayResult result = replayService.replayWithRetry(
                    event.getMethod(),
                    event.getHeaders(),
                    event.getPayload(),
                    sub.getTargetUrl());

            if (result.isSuccess()) {
                successCount++;
            }

            report.append("--- Delivery Report for ").append(sub.getTargetUrl()).append(" ---\n")
                    .append(result.getLog()).append("\n\n");
        }

        if (successCount == subs.size()) {
            event.setStatus("SUCCESS");
        } else if (successCount > 0) {
            event.setStatus("PARTIAL_SUCCESS");
        } else {
            event.setStatus("FAILED");
        }

        event.setDeliveryCount(subs.size());
        event.setDeliveryDetails(report.toString());
        event.setLastDeliveryAt(LocalDateTime.now());
        eventRepository.save(event);

        log.info("Event {} processed: {}/{} success", event.getId(), successCount, subs.size());
    }
}
