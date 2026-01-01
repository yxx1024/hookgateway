# Webhook Tunnel Agent

## 简介

Webhook Tunnel Agent 是 HookGateway 的本地开发工具，允许开发者将部署在公网的 Webhook 请求穿透到本地开发环境。

## 使用场景

- 微信支付/支付宝等需要公网 HTTPS 回调的本地调试
- 第三方 Webhook 集成的本地开发
- 无需 ngrok 等第三方工具

## 快速开始

### 1. 编译

```bash
cd tunnel-agent
mvn clean package
```

编译后会生成 `target/tunnel-agent.jar`

### 2. 运行

```bash
java -jar target/tunnel-agent.jar \
  --server=ws://your-gateway-server.com/tunnel/connect \
  --key=YOUR_TUNNEL_KEY \
  --target=http://localhost:8080/webhook
```

### 3. 参数说明

| 参数 | 说明 | 示例 |
|------|------|------|
| `--server` | HookGateway WebSocket 地址 | `ws://gateway.example.com/tunnel/connect` |
| `--key` | Tunnel Key（从管理界面获取） | `abc123-def456-789xyz` |
| `--target` | 本地服务地址 | `http://localhost:3000/api/webhook` |

## 工作原理

```
第三方服务 -----> 公网 HookGateway -----> WebSocket -----> Tunnel Agent -----> 本地服务
  (HTTPS)            (验签/过滤)           (加密传输)       (HTTP转发)      (localhost)
```

## 注意事项

- **安全性**：生产环境请使用 WSS (WebSocket over TLS)
- **网络**：确保本地网络可访问公网 HookGateway
- **重连**：Agent 支持自动重连，断线后会每5秒重试一次

## 示例输出

```
========================================
  Webhook Tunnel Agent
========================================
服务器: ws://gateway.example.com/tunnel/connect
Tunnel Key: abc123-def456
本地目标: http://localhost:8080/webhook
========================================
正在连接...
✅ 连接成功！等待 Webhook...

📥 收到 Webhook [ID: 12345]
   来源: wechat
   方法: POST
   载荷长度: 256 bytes
   ✅ 已转发到本地，响应: 200
```

## 故障排除

### 连接失败

- 检查服务器地址是否正确
- 确认 Tunnel Key 是否有效
- 检查防火墙设置

### 转发失败

- 确认本地服务是否运行
- 检查目标 URL 是否正确
- 查看本地服务日志

## 高级用法

### 生产环境（WSS）

```bash
java -jar tunnel-agent.jar \
  --server=wss://gateway.example.com/tunnel/connect \
  --key=YOUR_KEY \
  --target=http://localhost:8080/webhook
```

### 后台运行

```bash
nohup java -jar tunnel-agent.jar \
  --server=ws://... \
  --key=... \
  --target=... \
  > tunnel-agent.log 2>&1 &
```
