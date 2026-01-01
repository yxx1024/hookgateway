package com.example.tunnelagent;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.Map;

/**
 * Webhook Tunnel Agent
 * 连接到远程 HookGateway 并将 Webhook 转发到本地服务
 */
public class TunnelAgentMain {

    private static final Gson gson = new Gson();

    public static void main(String[] args) {
        String server = getArg(args, "--server", "ws://localhost:8080/tunnel/connect");
        String tunnelKey = getArg(args, "--key", null);
        String targetUrl = getArg(args, "--target", "http://localhost:8080/webhook");

        if (tunnelKey == null || tunnelKey.isEmpty()) {
            System.err.println("错误：必须提供 --key 参数");
            System.exit(1);
        }

        System.out.println("========================================");
        System.out.println("  Webhook Tunnel Agent");
        System.out.println("========================================");
        System.out.println("服务器: " + server);
        System.out.println("Tunnel Key: " + tunnelKey);
        System.out.println("本地目标: " + targetUrl);
        System.out.println("========================================");
        System.out.println("正在连接...");

        Map<String, String> headers = Map.of("X-Tunnel-Key", tunnelKey);
        TunnelWebSocketClient client = new TunnelWebSocketClient(URI.create(server), targetUrl, headers);
        client.connect();
    }

    private static String getArg(String[] args, String key, String defaultValue) {
        for (String arg : args) {
            if (arg.startsWith(key + "=")) {
                return arg.substring(key.length() + 1);
            }
        }
        return defaultValue;
    }

    static class TunnelWebSocketClient extends WebSocketClient {

        private final String targetUrl;

        public TunnelWebSocketClient(URI serverUri, String targetUrl, Map<String, String> headers) {
            super(serverUri, headers);
            this.targetUrl = targetUrl;
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            System.out.println("✅ 连接成功！等待 Webhook...");
        }

        @Override
        public void onMessage(String message) {
            try {
                JsonObject json = gson.fromJson(message, JsonObject.class);
                String type = json.get("type").getAsString();

                if ("WELCOME".equals(type)) {
                    System.out.println("📩 收到欢迎消息: " + json.get("message").getAsString());
                    return;
                }

                if ("WEBHOOK".equals(type)) {
                    long eventId = json.get("eventId").getAsLong();
                    String source = json.get("source").getAsString();
                    String method = json.get("method").getAsString();
                    String headers = json.has("headers") ? json.get("headers").getAsString() : "";
                    String payload = json.get("payload").getAsString();

                    System.out.println("\n📥 收到 Webhook [ID: " + eventId + "]");
                    System.out.println("   来源: " + source);
                    System.out.println("   方法: " + method);

                    // 转发到本地服务并返回结果描述
                    String result = forwardToLocal(method, headers, payload);
                    
                    // 发送 ACK 回网关
                    sendAck(eventId, result);
                }

            } catch (Exception e) {
                System.err.println("❌ 处理消息时出错: " + e.getMessage());
                e.printStackTrace();
            }
        }

        private void sendAck(long eventId, String result) {
            try {
                // 使用普通 HashMap 以防未来有字段为 null (Map.of 不支持 null)
                java.util.Map<String, Object> ack = new java.util.HashMap<>();
                ack.put("type", "ACK");
                ack.put("eventId", eventId);
                ack.put("status", result.startsWith("SUCCESS") ? "SUCCESS" : "FAILED");
                ack.put("detail", result);

                String ackJson = gson.toJson(ack);
                this.send(ackJson);
                System.out.println("   📤 ACK 已上报网关");
            } catch (Exception e) {
                System.err.println("   ❌ 发送 ACK 失败: " + e.getMessage());
            }
        }

        private String forwardToLocal(String method, String headersStr, String payload) {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                org.apache.hc.core5.http.ClassicHttpRequest request;
                
                // 根据原始方法创建请求
                switch (method.toUpperCase()) {
                    case "GET": request = new org.apache.hc.client5.http.classic.methods.HttpGet(targetUrl); break;
                    case "PUT": request = new org.apache.hc.client5.http.classic.methods.HttpPut(targetUrl); break;
                    case "DELETE": request = new org.apache.hc.client5.http.classic.methods.HttpDelete(targetUrl); break;
                    case "PATCH": request = new org.apache.hc.client5.http.classic.methods.HttpPatch(targetUrl); break;
                    case "HEAD": request = new org.apache.hc.client5.http.classic.methods.HttpHead(targetUrl); break;
                    default: request = new HttpPost(targetUrl); break;
                }

                // 设置 Body (如果是支持 Body 的方法)
                if (payload != null && !payload.isEmpty() && (request instanceof org.apache.hc.core5.http.HttpEntityContainer)) {
                    ((org.apache.hc.core5.http.HttpEntityContainer) request).setEntity(new StringEntity(payload, org.apache.hc.core5.http.ContentType.APPLICATION_JSON));
                }

                // 解析并设置 Headers
                if (headersStr != null && !headersStr.isEmpty()) {
                    String[] lines = headersStr.split("\n");
                    for (String line : lines) {
                        int colonIndex = line.indexOf(":");
                        if (colonIndex > 0) {
                            String name = line.substring(0, colonIndex).trim();
                            String value = line.substring(colonIndex + 1).trim();
                            // 安全加固：更严谨的 Header 过滤名单
                            boolean isRestricted = name.equalsIgnoreCase("Host") 
                                || name.equalsIgnoreCase("Content-Length") 
                                || name.equalsIgnoreCase("Connection") 
                                || name.equalsIgnoreCase("Content-Type")
                                || name.equalsIgnoreCase("Authorization") // 安全风险：不透传内网鉴权
                                || name.equalsIgnoreCase("Proxy-Authorization")
                                || name.equalsIgnoreCase("Set-Cookie");

                            if (!isRestricted) {
                                request.addHeader(name, value);
                            }
                        }
                    }
                }
                // 显式设置 Content-Type 防止丢失，并强制 UTF-8
                request.setHeader("Content-Type", "application/json");

                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    int statusCode = response.getCode();
                    System.out.println("   ✅ 已转发到本地，响应: " + statusCode);
                    return "SUCCESS: Local client responded with " + statusCode;
                }

            } catch (Exception e) {
                System.err.println("   ❌ 转发失败: " + e.getMessage());
                return "FAILED: " + e.getMessage();
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            System.out.println("\n⚠️  连接已关闭 (代码 " + code + "): " + reason);

            // 自动重连逻辑修复：必须在非 WebSocket 线程中运行
            if (remote || code == 1006) {
                System.out.println("5秒后尝试重连...");
                new Thread(() -> {
                    try {
                        Thread.sleep(5000);
                        System.out.println("🔄 正在尝试重新连接...");
                        this.reconnect();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            }
        }

        @Override
        public void onError(Exception ex) {
            System.err.println("❌ WebSocket 错误: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
