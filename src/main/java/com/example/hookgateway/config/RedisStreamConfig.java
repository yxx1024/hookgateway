package com.example.hookgateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

import java.time.Duration;

@Configuration
@Slf4j
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.distribution.mode", havingValue = "redis")
public class RedisStreamConfig {

        public static final String STREAM_KEY = "webhook:stream";
        public static final String GROUP_NAME = "webhook-group";
        // 动态生成消费者名称，支持多实例部署
        public static final String CONSUMER_NAME = "consumer-" + java.util.UUID.randomUUID().toString().substring(0, 8);

        @Bean
        public StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamMessageListenerContainer(
                        RedisConnectionFactory connectionFactory,
                        com.example.hookgateway.service.WebhookStreamConsumer consumer) {

                // 1. Initialize Stream and Group if not exists
                try {
                        connectionFactory.getConnection().streamCommands().xGroupCreate(
                                        STREAM_KEY.getBytes(), GROUP_NAME, ReadOffset.from("0"), true);
                } catch (Exception e) {
                        log.info("Stream or Group already exists, skipping initialization");
                }

                // 2. Container Options
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                                .builder()
                                .pollTimeout(Duration.ofSeconds(1))
                                .build();

                StreamMessageListenerContainer<String, MapRecord<String, String, String>> container = StreamMessageListenerContainer
                                .create(connectionFactory, options);

                // 3. Register Listener
                container.receive(
                                Consumer.from(GROUP_NAME, CONSUMER_NAME),
                                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),
                                consumer);

                container.start();
                return container;
        }
}
