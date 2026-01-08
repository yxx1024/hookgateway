package com.example.hookgateway.websocket;

import com.example.hookgateway.model.TunnelBroadcastMessage;
import com.example.hookgateway.model.WebhookEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 隧道会话管理器
 * 管理活跃的 WebSocket 隧道连接，支持分布式环境下的消息路由
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TunnelSessionManager {

    private final ObjectMapper objectMapper;
    private static final String EVENT_TUNNEL_PREFIX = "webhook:event:tunnel:";

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    // 键：tunnelKey，值：WebSocketSession
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    // V14: BOLA 保护的本地兜底缓存（限制大小）
    private final Map<Long, String> localEventTunnelMap = java.util.Collections
            .synchronizedMap(new java.util.LinkedHashMap<Long, String>() {
                protected boolean removeEldestEntry(Map.Entry<Long, String> eldest) {
                    return size() > 5000;
                }
            });

    /**
     * 将 Webhook 事件路由到正确的隧道客户端
     * 本地无连接时通过 Redis 广播到集群其他节点
     */
    public String routeEvent(WebhookEvent event, String tunnelKey) {
        // V13: BOLA 保护 - 路由前先登记映射
        registerEventTunnelMapping(event.getId(), tunnelKey);

        WebSocketSession session = getSession(tunnelKey);

        if (session != null && session.isOpen()) {
            return deliverToLocal(event, tunnelKey, session);
        }

        // 本地没有活跃连接，尝试广播到集群
        if (redisTemplate != null) {
            log.info("[TunnelManager] Local session missing for {}, broadcasting to cluster", tunnelKey);
            broadcastToCluster(event, tunnelKey);
            return "QUEUED: Broadcasted to cluster at " + LocalDateTime.now();
        }

        return "ERROR: Tunnel not connected and Redis not available";
    }

    /**
     * 处理来自集群其他节点的广播消息
     */
    public void handleBroadcast(TunnelBroadcastMessage msg) {
        WebSocketSession session = getSession(msg.getTunnelKey());
        if (session != null && session.isOpen()) {
            log.info("[TunnelManager] Handling cluster broadcast for tunnel: {}", msg.getTunnelKey());
            try {
                java.util.Map<String, Object> tunnelMessage = new java.util.HashMap<>();
                tunnelMessage.put("type", "WEBHOOK");
                tunnelMessage.put("eventId", msg.getEventId());
                tunnelMessage.put("source", msg.getSource());
                tunnelMessage.put("method", msg.getMethod());
                tunnelMessage.put("headers", msg.getHeaders() != null ? msg.getHeaders() : "");
                tunnelMessage.put("payload", msg.getPayload() != null ? msg.getPayload() : "");

                String jsonMessage = objectMapper.writeValueAsString(tunnelMessage);
                session.sendMessage(new TextMessage(jsonMessage));
            } catch (Exception e) {
                log.error("[TunnelManager] Failed to deliver broadcasted message to local tunnel", e);
            }
        }
    }

    private String deliverToLocal(WebhookEvent event, String tunnelKey, WebSocketSession session) {
        try {
            java.util.Map<String, Object> tunnelMessage = new java.util.HashMap<>();
            tunnelMessage.put("type", "WEBHOOK");
            tunnelMessage.put("eventId", event.getId());
            tunnelMessage.put("source", event.getSource());
            tunnelMessage.put("method", event.getMethod());
            tunnelMessage.put("headers", event.getHeaders() != null ? event.getHeaders() : "");
            tunnelMessage.put("payload", event.getPayload() != null ? event.getPayload() : "");

            String jsonMessage = objectMapper.writeValueAsString(tunnelMessage);
            session.sendMessage(new TextMessage(jsonMessage));

            log.info("[Tunnel] Sent webhook {} to local tunnel {}", event.getId(), tunnelKey);
            return "SUCCESS: Delivered to local tunnel at " + LocalDateTime.now();
        } catch (Exception e) {
            log.error("[Tunnel] Failed to send to local tunnel {}", tunnelKey, e);
            return "ERROR: WebSocket delivery failed - " + e.getMessage();
        }
    }

    private void broadcastToCluster(WebhookEvent event, String tunnelKey) {
        try {
            TunnelBroadcastMessage msg = TunnelBroadcastMessage.builder()
                    .tunnelKey(tunnelKey)
                    .eventId(event.getId())
                    .source(event.getSource())
                    .method(event.getMethod())
                    .headers(event.getHeaders())
                    .payload(event.getPayload())
                    .build();

            String json = objectMapper.writeValueAsString(msg);
            redisTemplate.convertAndSend(com.example.hookgateway.config.RedisPubSubConfig.TUNNEL_CHANNEL, json);
        } catch (Exception e) {
            log.error("[TunnelManager] Failed to broadcast tunnel message", e);
        }
    }

    /**
     * 注册新的隧道连接
     */
    public void registerSession(String tunnelKey, WebSocketSession session) {
        // 如果已存在，先关闭旧连接
        WebSocketSession oldSession = activeSessions.get(tunnelKey);
        if (oldSession != null && oldSession.isOpen()) {
            try {
                log.info("[TunnelSessionManager] Closing old session for tunnelKey: {}", tunnelKey);
                oldSession.close();
            } catch (IOException e) {
                log.error("[TunnelSessionManager] Error closing old session", e);
            }
        }

        activeSessions.put(tunnelKey, session);
        log.info("[TunnelSessionManager] Registered new session for tunnelKey: {}, Total active: {}",
                tunnelKey, activeSessions.size());
    }

    /**
     * 移除指定隧道连接，增加 session 校验避免竞态
     */
    public void removeSession(String tunnelKey, WebSocketSession session) {
        if (tunnelKey == null || session == null)
            return;

        // 只有当 Map 中的 session 是当前要移除的那个 session 时才移除
        // 防止：新连接 A 覆盖了旧连接 B 后，B 的 onClose 触发误删了 A
        boolean removed = activeSessions.remove(tunnelKey, session);
        if (removed) {
            log.info("[TunnelSessionManager] Removed session for tunnelKey: {}, Total active: {}",
                    tunnelKey, activeSessions.size());
        }
    }

    /**
     * 根据 tunnelKey 获取 WebSocket 会话
     */
    public WebSocketSession getSession(String tunnelKey) {
        WebSocketSession session = activeSessions.get(tunnelKey);
        if (session != null && !session.isOpen()) {
            // 清理已关闭的连接
            activeSessions.remove(tunnelKey);
            return null;
        }
        return session;
    }

    /**
     * 检查隧道是否在线
     */
    public boolean isConnected(String tunnelKey) {
        WebSocketSession session = getSession(tunnelKey);
        return session != null && session.isOpen();
    }

    /**
     * 获取活跃连接数
     */
    public int getActiveConnectionCount() {
        // 清理已关闭的连接
        activeSessions.entrySet().removeIf(entry -> !entry.getValue().isOpen());
        return activeSessions.size();
    }

    /**
     * V13: 将事件 ID 与允许处理该事件的 tunnelKey 绑定到 Redis 与本地缓存
     */
    public void registerEventTunnelMapping(Long eventId, String tunnelKey) {
        if (eventId == null || tunnelKey == null)
            return;

        // 先写入本地缓存
        localEventTunnelMap.put(eventId, tunnelKey);

        if (redisTemplate != null) {
            String key = EVENT_TUNNEL_PREFIX + eventId;
            try {
                redisTemplate.opsForValue().set(key, tunnelKey, java.time.Duration.ofHours(24));
            } catch (Exception e) {
                log.warn("Failed to cache event map to Redis", e);
            }
        }
    }

    /**
     * V13: 从 Redis 或本地缓存获取事件绑定的 tunnelKey
     */
    public String getTunnelKeyForEvent(Long eventId) {
        if (eventId == null)
            return null;

        // 先查本地（更快且可兜底）
        String key = localEventTunnelMap.get(eventId);
        if (key != null)
            return key;

        if (redisTemplate != null) {
            try {
                return redisTemplate.opsForValue().get(EVENT_TUNNEL_PREFIX + eventId);
            } catch (Exception e) {
                log.warn("Failed to read event map from Redis", e);
            }
        }
        return null;
    }
}
