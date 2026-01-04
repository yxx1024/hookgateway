# HookGateway 安全审计报告

日期：2026-01-04  
范围：主应用 + tunnel-agent  
方法：仅静态代码审查（未做运行时测试、依赖漏洞库扫描）  
CLAUDE.md：未找到  

## 总结
总体风险：高

主要原因：
- 公开的摄入接口接收不受限的请求体并持久化。
- 订阅默认验签为 NONE，除非管理员配置，否则可被伪造。
- Tunnel ACK 授权在映射缺失时存在放行缺口。

## 发现的问题

### 高-1：Webhook 请求体无上限，易导致 DoS 与存储耗尽
影响：  
任意未认证客户端可向 `/hooks/**` 发送超大请求体，写入数据库或 Redis，触发内存/磁盘耗尽，形成 DoS。

证据：  
- `src/main/java/com/example/hookgateway/controller/IngestController.java:48`（公开入口，`@RequestBody` 直接接收）  
- `src/main/java/com/example/hookgateway/model/WebhookEvent.java:20`（headers/payload 以 `TEXT` 存储）  
- `src/main/resources/application.properties:100`（multipart 限制不适用于 JSON body）  
- `src/main/resources/application.properties:103`（注释称限制请求体，但实际仅限制 header）  

建议：  
- 在服务端与反向代理层对 `/hooks/**` 设定硬性 body 上限（Tomcat/代理/过滤器兜底）。  
- 持久化前拒绝或截断超大 payload。  
- 修正配置注释，避免误导。  

### 高-2：默认不验签，摄入端点可被伪造
影响：  
若管理员未开启验签，任何人都能伪造指定 source 的 webhook 并被转发，污染下游系统。

证据：  
- `src/main/java/com/example/hookgateway/controller/IngestController.java:48`  
- `src/main/java/com/example/hookgateway/model/Subscription.java:40`（默认 `verifyMethod = NONE`）  

建议：  
- 新建订阅默认要求验签，或强制配置 source 的来源 IP 白名单。  
- 若开放入口为设计目标，需明确威胁模型并提供限速/WAF/签名指引。  

### 中-1：Tunnel ACK 授权在映射缺失时放行
影响：  
当 event-to-tunnel 映射缺失（缓存淘汰/Redis 异常/TTL 过期）时，任意已连接的 tunnel 可能 ACK 任意 `eventId` 并修改状态与日志。

证据：  
- `src/main/java/com/example/hookgateway/websocket/TunnelWebSocketHandler.java:87`（仅在映射存在且不匹配时才拦截）  
- `src/main/java/com/example/hookgateway/websocket/TunnelSessionManager.java:200`（映射只在缓存/Redis 中）  

建议：  
- 映射缺失时直接拒绝 ACK（fail closed）。  
- 将 tunnelKey 持久化到事件表或其他持久化存储，避免缓存失效导致授权缺口。  

### 中-2：SSRF 防护为黑名单 + DNS 校验，仍可绕过
影响：  
黑名单与 DNS 解析策略易被 IPv6 ULA、DNS rebinding 等手法绕过，可能被利用进行内网访问。

证据：  
- `src/main/java/com/example/hookgateway/utils/UrlValidator.java:33`  
- `src/main/java/com/example/hookgateway/service/ReplayService.java:96`  

建议：  
- 改为严格 allowlist（域名/IP 允许列表）。  
- 解析后对 IP 做固定绑定并阻断非公网地址（含 IPv6 私网）。  

### 中-3：敏感数据明文存储
影响：  
数据库泄露会暴露验签密钥、请求头和 payload，存在敏感信息外泄风险。

证据：  
- `src/main/java/com/example/hookgateway/model/Subscription.java:43`（verifySecret）  
- `src/main/java/com/example/hookgateway/model/WebhookEvent.java:20`（headers/payload）  

建议：  
- 对密钥做加密存储（如信封加密），对 payload/headers 做脱敏或按需存储。  

### 低-1：UI 依赖 CDN 资源存在供应链风险
影响：  
CDN 被污染会向后台管理页面注入恶意脚本。

证据：  
- `src/main/resources/templates/dashboard.html:6`（其它模板同样引用）  

建议：  
- 自托管静态资源，或增加 SRI + CSP。  

### 低-2：Tunnel Agent 默认使用明文 WebSocket
影响：  
未覆盖 `--server` 参数时，tunnel key 以明文传输。

证据：  
- `tunnel-agent/src/main/java/com/example/tunnelagent/TunnelAgentMain.java:25`  

建议：  
- 默认改为 `wss://`，并在文档中强调 TLS 要求。  

## 备注
- 管理端表单已包含 CSRF token，未发现缺失。  
- 未执行测试或依赖漏洞扫描。  

## 优先行动建议（最小改动、收益最高）
1) 为 `/hooks/**` 强制请求体上限。  
2) 新订阅默认要求验签或 IP 白名单。  
3) Tunnel ACK 映射缺失时直接拒绝。  
4) SSRF 策略改为 allowlist + DNS 绑定。  
5) 明确敏感数据存储策略（加密/脱敏/按需保留）。  
