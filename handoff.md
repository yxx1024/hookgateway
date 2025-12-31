# Webhook Gateway - 项目交接文档 (V10)

## 📌 项目概览
**Webhook Gateway** 是一个轻量级 Webhook 路由与管理网关，用于接收、持久化、过滤并分发 Webhook 事件。它解决了异构系统对接时，下游服务被无关事件干扰、无法追踪投递历史以及缺乏安全验证的问题。

- **当前版本**: V10 (Stable Release)
- **最后更新**: 2025-12-31
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
- **微信支付 RSA**: 实现了 `WechatPayVerifier`，完整支持微信支付 V3 回调的 RSA-SHA256 签名验证。
- **PEM 工具类**: 新增 `PemUtils` 解析 PEM 格式公钥。
- **配置化**: 订阅级别配置验证方式、密钥和签名 Header。

### 4. 用户体验与文档增强 (New)
- **双语支持**: 新增 `README_EN.md` 及中英文切换入口，支持国际化用户。
- **配置友好化**: 在 `application.properties` 中添加了详细的 MySQL/Redis 模板与注释，方便新手上手。
- **启动脚本修复**: 修复 `start.sh` 环境变量传递问题，增加构建失败检查。
- **性能演进规划**: 输出 `OPTIMIZATION_PLAN.md`，制定了从单机到亿级流量的架构演进路线图。
- **UI 优化**: 修复订阅列表溢出问题，优化表单折叠交互。

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
- `src/main/java/com/example/hookgateway/utils/PemUtils.java`: PEM 公钥解析工具。
- `src/main/java/com/example/hookgateway/controller/IngestController.java`: 核心摄入控制器。
- `OPTIMIZATION_PLAN.md`: **[NEW]** 性能优化与架构演进文档。
- `README_EN.md`: **[NEW]** 英文说明文档。

---

## 🔮 V11 待办 (Future Plans)
1. **Payload 变换 (Transformation)**: 实现 Webhook 内容转换功能（如使用 Freemarker/Velocity 模板引擎调整转发格式）。
2. **性能优化实施**: 根据 `OPTIMIZATION_PLAN.md`，在用户配置了 Redis/MySQL 时按需启用异步缓冲写入 (Write-Behind)。

---

## 🤖 接手 AI 指引
本阶段不仅完成了核心功能（验签、Redis 分发），还大幅提升了项目的**工程化水平**（文档国际化、脚本健壮性、配置易用性）。
你应该首先阅读 `OPTIMIZATION_PLAN.md` 以理解未来的架构方向。
接下来的核心开发任务是 **Payload 变换**，这将赋予网关强大的数据适配能力。
