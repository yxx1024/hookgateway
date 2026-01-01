package com.example.hookgateway.service;

import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class ReplayService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // V8: ThreadLocal to collect all retry attempts' logs
    private static final ThreadLocal<StringBuilder> logAccumulator = ThreadLocal.withInitial(StringBuilder::new);
    private static final ThreadLocal<Integer> attemptCounter = ThreadLocal.withInitial(() -> 0);

    private static final java.time.format.DateTimeFormatter LOG_DATE_FORMATTER = java.time.format.DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss");

    private void appendLog(String message) {
        StringBuilder sb = logAccumulator.get();
        if (sb.length() > 0)
            sb.append("\n");
        sb.append("[").append(java.time.LocalDateTime.now().format(LOG_DATE_FORMATTER)).append("] ").append(message);
    }

    private void clearLog() {
        logAccumulator.get().setLength(0);
        attemptCounter.set(0);
    }

    @Data
    @Builder
    public static class ReplayResult {
        private boolean success;
        private int statusCode;
        private String message;
        private String targetUrl;
        @Builder.Default
        private String log = "";
    }

    /**
     * V7: 带有指数退避重试的重放逻辑
     * V8: 增加了轨迹日志收集
     * V12: 优化重试参数并显式记录尝试次数
     */
    @org.springframework.retry.annotation.Retryable(retryFor = {
            RuntimeException.class }, maxAttempts = 3, backoff = @org.springframework.retry.annotation.Backoff(delay = 2000, multiplier = 2.0))
    public ReplayResult replayWithRetry(String method, String headersRaw, String payload, String targetUrl) {
        int attempt = attemptCounter.get() + 1;
        attemptCounter.set(attempt);
        
        appendLog("--- Attempt #" + attempt + " ---");
        ReplayResult result = replay(method, headersRaw, payload, targetUrl);

        if (!result.isSuccess()) {
            throw new RuntimeException("HTTP " + result.getStatusCode() + " / " + result.getMessage());
        }

        // If success, include the accumulated log
        result.setLog(logAccumulator.get().toString());
        return result;
    }

    @org.springframework.retry.annotation.Recover
    public ReplayResult recover(RuntimeException e, String method, String headersRaw, String payload,
            String targetUrl) {
        String finalLog = logAccumulator.get().toString();
        return ReplayResult.builder()
                .success(false)
                .statusCode(-1)
                .message("All retries failed: " + e.getMessage())
                .targetUrl(targetUrl)
                .log(finalLog)
                .build();
    }

    /**
     * Entry point to clear log if needed from outside, though usually handled in
     * IngestController
     */
    public void startNewTracking() {
        clearLog();
    }

    public ReplayResult replay(String method, String headersRaw, String payload, String targetUrl) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .method(method, HttpRequest.BodyPublishers.ofString(payload == null ? "" : payload));

            // Simple header parsing
            String[] lines = headersRaw.split("\n");
            for (String line : lines) {
                if (line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    // Skip restricted headers
                    if (!key.equalsIgnoreCase("content-length") && !key.equalsIgnoreCase("host")
                            && !key.equalsIgnoreCase("connection")) {
                        try {
                            requestBuilder.header(key, value);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
            appendLog(targetUrl + " -> HTTP " + response.statusCode() + (success ? " (SUCCESS)" : " (FAILED)"));

            return ReplayResult.builder()
                    .success(success)
                    .statusCode(response.statusCode())
                    .message("HTTP " + response.statusCode())
                    .targetUrl(targetUrl)
                    .log("") // Will be populated by replayWithRetry
                    .build();

        } catch (Exception e) {
            appendLog(targetUrl + " -> Error: " + e.getMessage());
            return ReplayResult.builder()
                    .success(false)
                    .statusCode(-1)
                    .message(e.getMessage())
                    .targetUrl(targetUrl)
                    .log("") // Will be populated by replayWithRetry
                    .build();
        }
    }
}
