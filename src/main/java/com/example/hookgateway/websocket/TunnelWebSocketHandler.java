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
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String tunnelKey = extractTunnelKey(session);

        if (tunnelKey == null || tunnelKey.isEmpty()) {
            log.warn("[TunnelWebSocket] Connection rejected: missing tunnelKey");
            session.close(CloseStatus.BAD_DATA.withReason("Missing tunnelKey"));
            return;
        }

        // 验证 tunnelKey 是否存在
        boolean exists = subscriptionRepository.findAll().stream()
                .anyMatch(sub -> tunnelKey.equals(sub.getTunnelKey()));

        if (!exists) {
            log.warn("[TunnelWebSocket] Connection rejected: invalid tunnelKey: {}", tunnelKey);
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Invalid tunnelKey"));
            return;
        }

        // 注册会话
        sessionManager.registerSession(tunnelKey, session);
        log.info("[TunnelWebSocket] Connection established for tunnelKey: {}", tunnelKey);

        // 发送欢迎消息
        Map<String, Object> welcomeMsg = Map.of(
                "type", "WELCOME",
                "message", "Tunnel connected successfully",
                "tunnelKey", tunnelKey);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(welcomeMsg)));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String tunnelKey = extractTunnelKey(session);
        log.debug("[TunnelWebSocket] Received message from {}: {}", tunnelKey, message.getPayload());

        // 客户端发来的消息（如心跳、响应等）
        // 目前主要是服务端单向推送，客户端响应暂时不处理
        // 未来可扩展为双向通信，接收客户端返回的 HTTP 响应
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String tunnelKey = extractTunnelKey(session);
        if (tunnelKey != null) {
            sessionManager.removeSession(tunnelKey);
            log.info("[TunnelWebSocket] Connection closed for tunnelKey: {}, status: {}",
                    tunnelKey, status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String tunnelKey = extractTunnelKey(session);
        log.error("[TunnelWebSocket] Transport error for tunnelKey: {}", tunnelKey, exception);

        if (tunnelKey != null) {
            sessionManager.removeSession(tunnelKey);
        }

        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    /**
     * 从 WebSocket Session 中提取 tunnelKey
     */
    private String extractTunnelKey(WebSocketSession session) {
        String query = session.getUri().getQuery();
        if (query != null && query.startsWith("key=")) {
            return query.substring(4);
        }
        return null;
    }
}
