# Webhook Gateway - 项目交接文档 (V10)

## 📌 项目概览
**Webhook Gateway** 是一个轻量级 Webhook 路由与管理网关，用于接收、持久化、过滤并分发 Webhook 事件。它解决了异构系统对接时，下游服务被无关事件干扰、无法追踪投递历史以及缺乏安全验证的问题。

- **当前版本**: V10 (Stable Release)
- **最后更新**: 2025-12-30
- **核心能力**: 摄入存储、多路分发、指数退避重试、**内容级过滤**、**实时 QPS 监控**、手动重放、自动清理、**高级签名验证 (HMAC + RSA)**。

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

## ✅ V10 版本里程碑 (开发中)

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
- **微信支付 RSA**: 实现了 `WechatPayVerifier`，完整支持微信支付 V3 回调的 RSA-SHA256 签名验证。
- **PEM 工具类**: 新增 `PemUtils` 解析 PEM 格式公钥。
- **配置化**: 订阅级别配置验证方式、密钥和签名 Header。

### 4. 用户体验优化 (UI/UX)
- **响应式布局**: 修复了订阅列表在窄屏下的溢出问题，通过横向滚动条和智能截断确保数据可见。
- **表单优化**: 将“过滤器”和“安全验证”配置项折叠收纳，大幅简化了订阅创建界面，提升易用性。
- **代码清理**: 移除了生产环境中未使用的测试代码 (`WebhookEventRepository` 测试方法)，保持代码库整洁。

---

## 📂 关键敏感信息
- **数据库连接**: 详见根目录 [mysql_data.md](file:///Users/edy/my/test/hookgateway/mysql_data.md)。
  - **Host**: `rm-bp1048net06j9495z5o.mysql.rds.aliyuncs.com` 
  - **User**: `yxx` / **DB**: `webhook`
- **Redis 连接**: 默认配置为 `127.0.0.1:6379`。

---

## 📂 项目结构导读
- `src/main/resources/templates/subscriptions.html`: 订阅管理页，包含安全验证配置。
- `src/main/resources/i18n/messages*.properties`: 国际化文本。
- `src/main/java/com/example/hookgateway/security/`: **验签逻辑** (VerifierFactory, HmacVerifier, **WechatPayVerifier**)。
- `src/main/java/com/example/hookgateway/utils/PemUtils.java`: **[NEW]** PEM 公钥解析工具。
- `src/main/java/com/example/hookgateway/controller/IngestController.java`: 核心摄入控制器。
- `src/main/java/com/example/hookgateway/service/WebhookStreamConsumer.java`: Redis Stream 消费者服务。

---
前端页面变更时候，注意国际化

---

## 🔮 V10 待办 (Future Plans)
1. **Payload 变换**: 实现 Webhook Transformation 功能（使用模板引擎调整转发格式）。
2. ~~**高级验签**: 接入微信支付/支付宝 RSA 验签。~~ ✅ 已完成 (微信支付)

---

**接手 AI 指引**:
本阶段已完成 V10 版本的核心安全升级：**Webhook 签名验证**（含 HMAC 和 **微信支付 RSA**）与 **Redis 分布式分发**。系统现在更安全且具备水平扩展能力。
接下来的重点是 **Payload 变换功能**，这将赋予网关强大的数据适配能力。
