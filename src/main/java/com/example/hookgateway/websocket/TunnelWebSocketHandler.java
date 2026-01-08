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
 * 隧道 WebSocket 处理器
 * 处理 Webhook 隧道的 WebSocket 连接
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TunnelWebSocketHandler extends TextWebSocketHandler {

    private final TunnelSessionManager sessionManager;
    private final SubscriptionRepository subscriptionRepository;
    private final com.example.hookgateway.repository.WebhookEventRepository eventRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 建立 WebSocket 连接后进行鉴权并注册会话。
     *
     * @param session 会话
     * @throws Exception 异常
     */
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

    /**
     * 处理客户端消息。
     *
     * @param session 会话
     * @param message 文本消息
     * @throws Exception 异常
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String tunnelKey = extractTunnelKey(session);
        String payload = message.getPayload();

        // 安全加固：下行数据长度逻辑校验 (防止恶意大文本注入)
        if (payload != null && payload.length() > 1024 * 16) {
            log.warn("[TunnelWebSocket] Rejected oversized message from tunnel {}: {} bytes", tunnelKey,
                    payload.length());
            return;
        }

        log.debug("[TunnelWebSocket] Received message from {}: {}", tunnelKey, payload);

        try {
            Map<String, Object> msg = objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {
            });
            String type = (String) msg.get("type");

            if ("ACK".equals(type)) {
                if (msg.get("eventId") == null)
                    return;

                Long eventId = ((Number) msg.get("eventId")).longValue();
                String status = (String) msg.get("status");
                String detail = (String) msg.get("detail");

                // 安全加固 (BOLA): 验证该事件是否确实路由给了该隧道
                String expectedTunnelKey = sessionManager.getTunnelKeyForEvent(eventId);

                // 修复：映射缺失（null）或不匹配时直接拒绝（fail closed）
                if (expectedTunnelKey == null || !expectedTunnelKey.equals(tunnelKey)) {
                    log.warn(
                            "[TunnelWebSocket] BOLA ATTEMPT OR MAPPING EXPIRED! Tunnel {} tried to ACK event {} which belongs to tunnel {} (or null)",
                            tunnelKey, eventId, expectedTunnelKey);
                    return;
                }

                // 安全加固：状态字段校验（允许系统定义的标准状态）
                java.util.List<String> allowedStatuses = java.util.Arrays.asList("SUCCESS", "FAILED", "PARTIAL_SUCCESS",
                        "RECEIVED");
                if (!allowedStatuses.contains(status)) {
                    log.warn("[TunnelWebSocket] Invalid ACK status from tunnel {}: {}", tunnelKey, status);
                    return;
                }

                // 安全加固：详情字段截断（增加到 2000 字符，兼顾调试需求）
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
                    event.setDeliveryDetails(
                            oldDetails + reportHeader + (finalDetail != null ? finalDetail : "No details"));
                    eventRepository.save(event);
                });
            }
        } catch (Exception e) {
            log.error("[TunnelWebSocket] Failed to process message from tunnel {}: {}", tunnelKey, payload, e);
        }
    }

    /**
     * 连接关闭后清理会话。
     *
     * @param session 会话
     * @param status  关闭状态
     * @throws Exception 异常
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String tunnelKey = extractTunnelKey(session);
        if (tunnelKey != null) {
            sessionManager.removeSession(tunnelKey, session);
            log.info("[TunnelWebSocket] Connection closed for tunnelKey: {}, status: {}",
                    tunnelKey, status);
        }
    }

    /**
     * 处理传输异常并清理会话。
     *
     * @param session   会话
     * @param exception 异常
     * @throws Exception 异常
     */
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
     * V11.1: 从 WebSocket 握手请求头中提取 tunnelKey
     * 生产环境下建议通过请求头传输，避免在日志中暴露 URL 参数
     */
    private String extractTunnelKey(WebSocketSession session) {
        // 优先从请求头获取（推荐）
        String headerKey = session.getHandshakeHeaders().getFirst("X-Tunnel-Key");
        if (headerKey != null && !headerKey.isEmpty()) {
            return headerKey;
        }

        // V18: 仅允许请求头认证（安全加固）
        // 禁用 Query 参数，避免在日志中泄露密钥
        return null;
    }
}
