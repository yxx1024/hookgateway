package com.example.hookgateway.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tunnel Session Manager
 * 管理活跃的 WebSocket Tunnel 连接
 */
@Component
@Slf4j
public class TunnelSessionManager {

    // Key: tunnelKey, Value: WebSocketSession
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    /**
     * 注册一个新的 Tunnel 连接
     * 如果已存在相同 tunnelKey 的连接，会先关闭旧连接
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
     * 移除 Tunnel 连接
     */
    public void removeSession(String tunnelKey) {
        WebSocketSession removed = activeSessions.remove(tunnelKey);
        if (removed != null) {
            log.info("[TunnelSessionManager] Removed session for tunnelKey: {}, Total active: {}",
                    tunnelKey, activeSessions.size());
        }
    }

    /**
     * 根据 tunnelKey 获取 WebSocket Session
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
     * 检查 Tunnel 是否在线
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
}
