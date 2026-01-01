package com.example.hookgateway.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Tunnel 广播消息模型
 * 用于在分布式环境下，将 Webhook 消息分发给连接在不同实例上的 Tunnel 客户端
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TunnelBroadcastMessage implements Serializable {
    private String tunnelKey;
    private Long eventId;
    private String source;
    private String method;
    private String headers;
    private String payload;
}
