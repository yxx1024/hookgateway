# Webhook Gateway - 项目交接文档 (V10)

## 📌 项目概览
**Webhook Gateway** 是一个轻量级 Webhook 路由与管理网关，用于接收、持久化、过滤并分发 Webhook 事件。它解决了异构系统对接时，下游服务被无关事件干扰、无法追踪投递历史以及缺乏安全验证的问题。

- **当前版本**: V10 (Stable Release)
- **最后更新**: 2025-12-31
- **核心能力**: 摄入存储、多路分发、指数退避重试、**内容级过滤**、**实时 QPS 监控**、手动重放、自动清理、**高级签名验证 (HMAC + RSA)**、**Webhook Tunneling (内网穿透)**。

---

## 🛠 技术栈
- **语言/环境**: Java 17 / Maven
- **核心框架**: Spring Boot 3.2.1
- **数据库**: 
  - 当前开发环境: H2 (File)
  - **待迁移环境**: MySQL 8.0 (RDS Aliyun) - 详见 `mysql_data.md`
- **过滤引擎**: Jayway JsonPath / Regex
- **观测性**: Spring Boot Actuator + Custom QPS Logic
- **前端**: Thymeleaf / Tailwind CSS (Glassmorphism Style)

---

## ✅ V10 版本里程碑 (已完成)

### 1. 数据库架构升级
- **MySQL 迁移**: 已从 H2 文件数据库迁移至阿里云 RDS MySQL 8.0。
- **环境隔离**: 支持通过配置文件在 H2 和 MySQL 之间切换（默认生产使用 MySQL）。
- **初始化脚本**: 提供 `init_mysql.sql` 用于快速建表。

### 2. 分布式分发能力
- **Redis Stream 集成**: 引入 Redis Stream 作为异步消息队列，替代单机 `@Async`。
- **可选依赖**: Redis 组件设计为 Lazy Loading，仅在 `app.distribution.mode=redis` 时加载。
- **高可用**: 支持多实例部署，通过消费组实现消息的负载均衡与容错。

### 3. Webhook 签名验证框架
- **安全增强**: 实现了 `VerifierStrategy` 策略模式。
- **HMAC-SHA256**: 支持 GitHub 等平台的 HMAC 签名验证。
- **微信支付 V3 (完整版)**: 
  - 实现了 `WechatPayVerifier`，支持 RSA-SHA256 验签。
  - **证书自动轮换**: 支持配置多组 `[序列号, 公钥]` 对，系统根据 `Wechatpay-Serial` 头自动匹配公钥。
  - **向后兼容**: 仍完美支持旧版单 PEM 证书配置。
- **PEM 工具类**: 新增 `PemUtils` 解析 PEM 格式公钥。
- **配置化 UI**: 订阅管理页新增了 **证书管理弹窗 (Cert Manager)**，支持友好地管理多套证书，并提供中英双语界面。

### 4. 性能与架构优化 (New)
- **Write-Behind (异步缓冲写入)**: 
  - 实现了高并发摄入优化的核心机制。
  - **模式**: `app.ingest.mode=redis`。此模式下，网关接收请求后仅做简单校验，立即将事件推送到 Redis Stream (`webhook:events:ingest`) 并返回 202，从而将摄入吞吐量与数据库写入解耦。
  - **批量持久化**: 新增 `EventPersister` 服务，定时批量消费 Redis 流并将数据落库，随后再推送到分发流。
- **文档与工程化**:
  - **双语支持**: 新增 `README_EN.md` 及中英文切换入口。
  - **配置友好化**: `application.properties` 添加了详细注释。
  - **脚本修复**: 优化 `start.sh`。
  - **演进规划**: 输出 `OPTIMIZATION_PLAN.md`。

---

## 📂 关键敏感信息
- **数据库连接**: 详见根目录 [mysql_data.md](file:///Users/edy/my/test/hookgateway/mysql_data.md)。
  - **Host**: `rm-bp1048net06j9495z5o.mysql.rds.aliyuncs.com` 
  - **User**: `yxx` / **DB**: `webhook`
- **Redis 连接**: 默认配置为 `127.0.0.1:6379`。

---

## 📂 项目结构导读
---
 
 ## 🚀 V11: Webhook Tunneling (内网穿透)
 本版本引入了基于 WebSocket 的隧道功能，允许处于内网或防火墙后的应用接收 Webhook 事件，无需配置公网 IP 或 NAT。
 
 ### 1. 设计原理
 - **持久连接**: 客户端（`tunnel-agent`）通过 WebSocket 与网关建立长连接。
 - **密钥鉴权**: 客户端在握手时需在 Header 中携带 `X-Tunnel-Key`。网关通过 `SubscriptionRepository.findByTunnelKey` 进行高效验证。
 - **分布式路由 (Pub/Sub)**: 当 Webhook 摄入实例与客户端连接实例不一致时，通过 Redis 频道 `tunnel:broadcast` 实现跨节点转发。
 
 ### 2. 关键组件
 - `com.example.hookgateway.websocket.TunnelSessionManager`: 维护 `tunnel-key` 与 `WebSocketSession` 的映射，支持状态查询。
 - `com.example.hookgateway.websocket.TunnelWebSocketHandler`: 处理 WebSocket握手、消息心跳及断开重连逻辑。
 - `com.example.hookgateway.controller.TunnelController`: 提供 Tunnel Key 生成及在线状态监控 API。
 - `tunnel-agent/`: Java 编写的示例客户端，用于接收并转发隧道消息。
 
 ### 3. 隧道相关 API
 - **WebSocket 端点**: `ws://{host}:8080/tunnel/connect` (需携带 Header `X-Tunnel-Key`)
 - **生成 Key**: `POST /api/tunnel/generate-key`
 - **状态查询**: `GET /api/tunnel/status/{tunnelKey}`
 
 ---
 
- `src/main/resources/templates/subscriptions.html`: 订阅管理页 (新增证书管理 Modal)。
- `src/main/resources/i18n/messages*.properties`: 国际化文本 (新增证书管理相关词条)。
- `src/main/java/com/example/hookgateway/security/`: **验签逻辑** (VerifierFactory, HmacVerifier, **WechatPayVerifier**)。
- `src/main/java/com/example/hookgateway/service/EventPersister.java`: **[NEW]** 批量持久化服务 (Write-Behind)。
- `src/main/java/com/example/hookgateway/controller/IngestController.java`: 核心摄入控制器 (支持 Async Ingest)。
- `OPTIMIZATION_PLAN.md`: **[NEW]** 性能优化与架构演进文档。
- `README_EN.md`: **[NEW]** 英文说明文档。

---

## 🔮 V11 待办 (Future Plans)
1. **Payload 变换 (Transformation)**: 实现 Webhook 内容转换功能（如使用 Freemarker/Velocity 模板引擎调整转发格式）。
2. **性能压测**: 对 Write-Behind 模式进行极限压测，验证 `app.ingest.mode=redis` 对 QPS 的提升效果。

---

## 🤖 接手 AI 指引
本阶段不仅完成了核心功能（验签、Redis 分发），还**提前完成了** P0 级的性能优化任务——**Write-Behind (异步写入)**，并完善了微信支付的**证书轮换**支持。
项目现在具备了应对高并发和企业级安全需求的基础。
接下来的核心开发任务是 **Payload 变换**，这将赋予网关强大的数据适配能力。
