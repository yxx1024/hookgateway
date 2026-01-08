package com.example.hookgateway.config;

import com.example.hookgateway.model.TunnelBroadcastMessage;
import com.example.hookgateway.websocket.TunnelSessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.stereotype.Component;

/**
 * Redis Pub/Sub 配置。
 */
@Configuration
@Slf4j
public class RedisPubSubConfig {

    public static final String TUNNEL_CHANNEL = "tunnel:broadcast";

    /**
     * Redis 消息监听容器。
     *
     * @param connectionFactory Redis 连接工厂
     * @param listenerAdapter   监听适配器
     * @return 监听容器
     */
    @Bean
    public RedisMessageListenerContainer redisContainer(RedisConnectionFactory connectionFactory,
                                                        MessageListenerAdapter listenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listenerAdapter, new ChannelTopic(TUNNEL_CHANNEL));
        return container;
    }

    /**
     * 监听适配器。
     *
     * @param listener 监听器
     * @return 适配器
     */
    @Bean
    public MessageListenerAdapter listenerAdapter(TunnelBroadcastListener listener) {
        // 显式指定处理方法名为 handleMessage
        return new MessageListenerAdapter(listener, "handleMessage");
    }

    @Component
    @RequiredArgsConstructor
    public static class TunnelBroadcastListener {
        private final TunnelSessionManager sessionManager;
        private final ObjectMapper objectMapper;

        /**
         * 处理广播消息。
         *
         * @param message JSON 消息
         */
        public void handleMessage(String message) {
            try {
                TunnelBroadcastMessage broadcastMsg = objectMapper.readValue(message, TunnelBroadcastMessage.class);
                log.debug("[RedisPubSub] Received broadcast for tunnel: {}", broadcastMsg.getTunnelKey());
                sessionManager.handleBroadcast(broadcastMsg);
            } catch (Exception e) {
                log.error("[RedisPubSub] Failed to parse tunnel broadcast message", e);
            }
        }
    }
}
