package com.example.hookgateway.config;

import com.example.hookgateway.websocket.TunnelWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket Configuration
 * 配置 Webhook Tunneling 的 WebSocket 端点
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final TunnelWebSocketHandler tunnelWebSocketHandler;

    @org.springframework.beans.factory.annotation.Value("${app.security.websocket.allowed-origins:*}")
    private String allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = allowedOrigins.split(",");
        registry.addHandler(tunnelWebSocketHandler, "/tunnel/connect")
                .setAllowedOrigins(origins);
    }
}
