# Webhook Gateway - 项目交接文档 (V12)

## 📌 项目概览
**Webhook Gateway** 是一个轻量级 Webhook 路由与管理网关，用于接收、持久化、过滤并分发 Webhook 事件。它解决了异构系统对接时，下游服务被无关事件干扰、无法追踪投递历史以及缺乏安全验证的问题。

- **当前版本**: V12 (Performance & Replay Enhanced)
- **最后更新**: 2026-01-02
- **核心能力**: 摄入存储、多路分发、**全链路指数退避重试**、内容级过滤、实时 QPS 监控、**Tunnel 重放**、自动清理、高级签名验证 (HMAC + RSA)、Webhook Tunneling (内网穿透)。

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

### 5. 系统安全加固 (Completed)
- **密钥安全**: Tunnel Key 生成逻辑升级为 `SecureRandom`，杜绝伪随机预测风险。
- **ReDoS 防护**: 正则过滤通过 `CompletableFuture` 实现了 **超时熔断 (100ms)** 与 **长度限制 (50 chars)**。
- **配置安全**: 移除了代码中的默硬编码密码，统一由环境变量 `ADMIN_INIT_PASSWORD` 控制。
- **SSRF 深度防御**: 强制 JVM DNS 缓存 TTL=60s，有效防御 DNS Rebinding 攻击。
- **WebSocket 防护**: 收紧了 `allowed-origins` 策略，默认仅允许 Localhost 连接。

---

## 📂 关键敏感信息
- **数据库连接**: 详见根目录 [mysql_data.md](file:///Users/edy/my/test/hookgateway/mysql_data.md)。
  - **Host**: `rm-bp1048net06j9495z5o.mysql.rds.aliyuncs.com` 
  - **User**: `yxx` / **DB**: `webhook`
- **Redis 连接**: 默认配置为 `127.0.0.1:6379`。

---

## 📂 项目结构导读
---
 
### 3. Webhook Tunneling (内网穿透)
本版本引入了基于 WebSocket 的隧道功能，允许处于内网或防火墙后的应用接收 Webhook 事件，无需配置公网 IP 或 NAT。

- **持久连接**: 客户端（`tunnel-agent`）通过 WebSocket 与网关建立长连接。
- **分布式路由 (Pub/Sub)**: 支持跨节点转发，连接在 A 实例的客户端可接收 B 实例摄入的消息。
- **[NEW] Tunnel 重放**: 详情页可自动检测事件的历史隧道 Key，支持一键将历史事件重新泵入内网隧道，无需手动拼凑 URL。

### 4. 生产级重试与状态机 (New V12)
- **全链路指数退避**: 手动重放现在也支持 `2s -> 4s -> 8s` 的指数退避重试，并清晰记录 `Attempt #N` 轨迹。
- **智能状态机**: 修复了重放状态覆盖问题。手动重放失败时，如果之前已成功，则状态降级为 `PARTIAL_SUCCESS`（黄色），而非直接覆盖，确保分发状态的真实性。
- **泛型安全**: 消除了 WebSocket Handler 中的所有 Unchecked 编译警告。
 
 ---
 
- `src/main/resources/templates/subscriptions.html`: 订阅管理页 (新增证书管理 Modal)。
- `src/main/resources/i18n/messages*.properties`: 国际化文本 (新增证书管理相关词条)。
- `src/main/java/com/example/hookgateway/security/`: **验签逻辑** (VerifierFactory, HmacVerifier, **WechatPayVerifier**)。
- `src/main/java/com/example/hookgateway/service/EventPersister.java`: **[NEW]** 批量持久化服务 (Write-Behind)。
- `src/main/java/com/example/hookgateway/controller/IngestController.java`: 核心摄入控制器 (支持 Async Ingest)。
- `OPTIMIZATION_PLAN.md`: **[NEW]** 性能优化与架构演进文档。
- `README_EN.md`: **[NEW]** 英文说明文档。

---

## 🔮 V13 待办 (Future Plans)
1. **Payload 变换 (Transformation)**: 实现 Webhook 内容转换功能（如使用 Freemarker/Velocity 模板引擎调整转发格式）。
2. **多租户隔离**: 引入 Workspace 概念，实现不同团队/项目的配置物理隔离。

---

## 🤖 接手 AI 指引
本阶段（V12）极大地增强了系统的**鲁棒性与开发体验**。
1. **核心突破**：实现了 Webhook Tunnel 的**闭环重放**，让内网调试变得极其高效；
2. **生产加固**：重写了重放逻辑，引入了透明的**指数退避重试**（Attempt-based logging），并建立了健壮的状态流转机制。
项目现在已经达到了“生产级”的稳定度。接下来的重点应重回 **Payload 变换**，这是将网关打造成全能集成平台的最后一块拼图。
