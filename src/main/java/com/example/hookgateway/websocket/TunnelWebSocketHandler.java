package com.example.hookgateway.websocket;

import com.example.hookgateway.repository.SubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;

/**
 * Tunnel WebSocket Handler
 * 处理 Webhook Tunneling 的 WebSocket 连接
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TunnelWebSocketHandler extends TextWebSocketHandler {

    private final TunnelSessionManager sessionManager;
    private final SubscriptionRepository subscriptionRepository;
    private final com.example.hookgateway.repository.WebhookEventRepository eventRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String tunnelKey = extractTunnelKey(session);

        if (tunnelKey == null || tunnelKey.isEmpty()) {
            log.warn("[TunnelWebSocket] Connection rejected: missing X-Tunnel-Key header");
            session.close(CloseStatus.BAD_DATA.withReason("Missing X-Tunnel-Key header"));
            return;
        }

        // V11.1: 优化为精准查询，避免全表扫描
        boolean exists = subscriptionRepository.findByTunnelKey(tunnelKey).isPresent();

        if (!exists) {
            log.warn("[TunnelWebSocket] Connection rejected: invalid tunnelKey: {}", tunnelKey);
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Invalid tunnelKey"));
            return;
        }

        // 注册会话
        sessionManager.registerSession(tunnelKey, session);
        log.info("[TunnelWebSocket] Connection established for tunnelKey: {} (Session ID: {})", 
                tunnelKey, session.getId());

        // 发送欢迎消息
        Map<String, Object> welcomeMsg = Map.of(
                "type", "WELCOME",
                "status", "CONNECTED",
                "message", "Tunnel connected successfully via Header Auth",
                "tunnelKey", tunnelKey);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(welcomeMsg)));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String tunnelKey = extractTunnelKey(session);
        String payload = message.getPayload();
        
        // 安全加固：下行数据长度逻辑校验 (防止恶意大文本注入)
        if (payload != null && payload.length() > 1024 * 16) { 
            log.warn("[TunnelWebSocket] Rejected oversized message from tunnel {}: {} bytes", tunnelKey, payload.length());
            return;
        }

        log.debug("[TunnelWebSocket] Received message from {}: {}", tunnelKey, payload);

        try {
            Map<String, Object> msg = objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {});
            String type = (String) msg.get("type");

            if ("ACK".equals(type)) {
                if (msg.get("eventId") == null) return;
                
                Long eventId = ((Number) msg.get("eventId")).longValue();
                String status = (String) msg.get("status");
                String detail = (String) msg.get("detail");

                // 安全加固：Status 字段校验 (允许系统定义的标准状态)
                java.util.List<String> allowedStatuses = java.util.Arrays.asList("SUCCESS", "FAILED", "PARTIAL_SUCCESS", "RECEIVED");
                if (!allowedStatuses.contains(status)) {
                    log.warn("[TunnelWebSocket] Invalid ACK status from tunnel {}: {}", tunnelKey, status);
                    return;
                }

                // 安全加固：Detail 字段截断 (增加到 2000 字符，兼顾调试需求)
                if (detail != null && detail.length() > 2000) {
                    detail = detail.substring(0, 2000) + "...(truncated)";
                }

                log.info("[TunnelWebSocket] Received ACK for event {}: status={}, detail={}", eventId, status, detail);

                final String finalDetail = detail;
                eventRepository.findById(eventId).ifPresent(event -> {
                    event.setStatus(status);
                    // 拼接报告，带有长度保护
                    String reportHeader = "\n--- Tunnel Client ACK ---\n";
                    String oldDetails = event.getDeliveryDetails() != null ? event.getDeliveryDetails() : "";
                    event.setDeliveryDetails(oldDetails + reportHeader + (finalDetail != null ? finalDetail : "No details"));
                    eventRepository.save(event);
                });
            }
        } catch (Exception e) {
            log.error("[TunnelWebSocket] Failed to process message from tunnel {}: {}", tunnelKey, payload, e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String tunnelKey = extractTunnelKey(session);
        if (tunnelKey != null) {
            sessionManager.removeSession(tunnelKey, session);
            log.info("[TunnelWebSocket] Connection closed for tunnelKey: {}, status: {}",
                    tunnelKey, status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String tunnelKey = extractTunnelKey(session);
        log.error("[TunnelWebSocket] Transport error for tunnelKey: {}", tunnelKey, exception);

        if (tunnelKey != null) {
            sessionManager.removeSession(tunnelKey, session);
        }

        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    /**
     * V11.1: 从 WebSocket Handshake Headers 中提取 tunnelKey
     * 生产环境下建议通过 Header 传输，避免在日志中暴露 URL 参数
     */
    private String extractTunnelKey(WebSocketSession session) {
        // 优先从 Header 获取 (推荐)
        String headerKey = session.getHandshakeHeaders().getFirst("X-Tunnel-Key");
        if (headerKey != null && !headerKey.isEmpty()) {
            return headerKey;
        }

        // 后备方案：从 Query 参数获取 (为了兼容旧版但记录警告)
        String query = session.getUri().getQuery();
        if (query != null && query.contains("key=")) {
            log.warn("[TunnelWebSocket] Client used insecure URL param for tunnelKey. Use X-Tunnel-Key header instead.");
            // 简单解析 key=xxx
            for (String param : query.split("&")) {
                if (param.startsWith("key=")) {
                    return param.substring(4);
                }
            }
        }
        return null;
    }
}
