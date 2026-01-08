package com.example.hookgateway.config;

import com.example.hookgateway.websocket.TunnelWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置
 * 配置 Webhook 隧道的 WebSocket 端点
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final TunnelWebSocketHandler tunnelWebSocketHandler;

    @org.springframework.beans.factory.annotation.Value("${app.security.websocket.allowed-origins:*}")
    private String allowedOrigins;

    /**
     * 注册 WebSocket 处理器。
     *
     * @param registry 注册器
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = allowedOrigins.split(",");
        registry.addHandler(tunnelWebSocketHandler, "/tunnel/connect")
                .setAllowedOrigins(origins);
    }
}
