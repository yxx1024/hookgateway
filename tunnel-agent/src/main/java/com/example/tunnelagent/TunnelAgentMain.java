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
            System.err.println(
                    "使用方法：java -jar tunnel-agent.jar --server=ws://server/tunnel/connect --key=YOUR_KEY --target=http://localhost:8080/webhook");
            System.exit(1);
        }

        String wsUrl = server + "?key=" + tunnelKey;

        System.out.println("========================================");
        System.out.println("  Webhook Tunnel Agent");
        System.out.println("========================================");
        System.out.println("服务器: " + server);
        System.out.println("Tunnel Key: " + tunnelKey);
        System.out.println("本地目标: " + targetUrl);
        System.out.println("========================================");
        System.out.println("正在连接...");

        TunnelWebSocketClient client = new TunnelWebSocketClient(URI.create(wsUrl), targetUrl);
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

        public TunnelWebSocketClient(URI serverUri, String targetUrl) {
            super(serverUri);
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
                    String payload = json.get("payload").getAsString();

                    System.out.println("\n📥 收到 Webhook [ID: " + eventId + "]");
                    System.out.println("   来源: " + source);
                    System.out.println("   方法: " + method);
                    System.out.println("   载荷长度: " + payload.length() + " bytes");

                    // 转发到本地服务
                    forwardToLocal(method, payload);
                }

            } catch (Exception e) {
                System.err.println("❌ 处理消息时出错: " + e.getMessage());
                e.printStackTrace();
            }
        }

        private void forwardToLocal(String method, String payload) {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpPost request = new HttpPost(targetUrl);
                request.setEntity(new StringEntity(payload));
                request.setHeader("Content-Type", "application/json");

                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    int statusCode = response.getCode();
                    System.out.println("   ✅ 已转发到本地，响应: " + statusCode);
                }

            } catch (Exception e) {
                System.err.println("   ❌ 转发失败: " + e.getMessage());
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            System.out.println("\n⚠️  连接已关闭");
            System.out.println("   原因: " + reason);
            System.out.println("   代码: " + code);

            // 自动重连
            if (remote) {
                System.out.println("5秒后尝试重连...");
                try {
                    Thread.sleep(5000);
                    this.reconnect();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Override
        public void onError(Exception ex) {
            System.err.println("❌ WebSocket 错误: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
