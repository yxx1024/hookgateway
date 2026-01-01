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

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(tunnelWebSocketHandler, "/tunnel/connect")
                .setAllowedOrigins("*"); // 生产环境应限制为特定域名
    }
}
