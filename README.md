# HookGateway - 高性能 Webhook 路由与管理网关

<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2.1-brightgreen.svg" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Java-17-blue.svg" alt="Java 17">
  <img src="https://img.shields.io/badge/Docker-Ready-2496ED.svg" alt="Docker">
  <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License">
</p>

<p align="center">
  <strong>中文</strong> | <a href="./README_EN.md">English</a>
</p>

## 🚀 项目简介

**HookGateway** 是一个轻量级、可扩展的 Webhook 路由与管理平台。它专为解决异构系统集成中的 Webhook 痛点而设计，提供摄入存储、多路分发、指数退避重试、内容级过滤、实时监控等核心能力。

## ✨ 核心特性

| 特性 | 描述 |
|------|------|
| 📥 **异构摄入** | 统一接收来自不同来源的 Webhook 请求 |
| 🛡️ **安全验签** | HMAC-SHA256 / 微信支付 RSA-SHA256 |
| 🔍 **内容过滤** | JSONPath 与 Regex 规则 |
| 🔃 **一键重放** | 历史事件支持通过原 URL 或 Tunnel 快速重发 |
| 🔁 **智能重试** | 全链路指数退避 + Attempt 轨迹追踪 |
| 🚇 **内网穿透** | WebSocket Tunnel，支持分布式消息广播 |
| 🐳 **Docker 支持** | 一键部署，H2/MySQL/Redis 可选 |

## 🌟 使用场景

### 1. 统一 Webhook 入口与分发
当有多个异构服务（如 GitHub, Stripe, 微信支付）需要通知内部系统时，HookGateway 作为统一入口，接收并处理，然后分发给后端多个微服务。
* **示例**: GitHub Push 事件 -> CI 构建服务 + Slack 通知机器人 + 审计日志服务。

### 2. 增强 Webhook 可靠性
第三方平台通常重试机制有限。HookGateway 提供持久化存储和指数退避重试，即使内部消费者服务暂时宕机，消息也不会丢失，待服务恢复后自动重放。

### 3. 安全验证网关
将复杂的签名验证逻辑（如微信支付的 RSA-SHA256、GitHub 的 HMAC）下沉到网关层。内部服务无需重复实现验签代码，只需关注业务逻辑，且内网通信更安全。

### 4. 精细化路由与过滤
不需要处理所有事件？通过 JSONPath 或正则规则，仅将关键事件（如 `status == 'paid'`）转发给核心服务，过滤掉无关噪音（如 `ping` 或 `read` 事件），降低下游压力。

### 5. 内网开发与调试
在本地开发环境或没有公网 IP 的服务器上，无法接收回调？启用 **Webhook Tunnel**。网关通过 WebSocket 将 Webhook 实时“泵入”你的内网应用，开发者无需暴露公网端口或使用昂贵的内网穿透工具。

## 🏁 快速启动

### 方式一：Docker (推荐)

```bash
# 单机部署 (H2 内存数据库，无需 MySQL/Redis)
ADMIN_PASSWORD="your_strong_password" docker-compose up hookgateway

# 完整部署 (MySQL + Redis)
ADMIN_PASSWORD="your_strong_password" docker-compose --profile full up
```

### 方式二：本地运行

```bash
# 使用启动脚本 (推荐)
# 1. 打开脚本配置数据库/Redis信息: vi start.sh
# 2. 运行脚本 (会自动编译并启动)
# 3. 首次启动需设置 ADMIN_PASSWORD
export ADMIN_PASSWORD="your_strong_password"
./start.sh

# 或手动运行
export ADMIN_PASSWORD="your_strong_password"
mvn clean package -DskipTests
java -jar target/hookgateway-0.0.1-SNAPSHOT.jar
```

### 访问

- **Dashboard**: http://localhost:8080
- **H2 Console**: http://localhost:8080/h2-console (需启用 H2_CONSOLE_ENABLED)
- **初始登录**: `admin` / `${ADMIN_PASSWORD}` (未设置则不会创建账号)

## 🐳 Docker 部署选项

| 命令 | 数据库 | Redis | 适用场景 |
|------|--------|-------|----------|
| `docker-compose up hookgateway` | H2 内存 | ❌ | 开发/演示 |
| `docker-compose --profile mysql up` | MySQL | ❌ | 单机生产 |
| `docker-compose --profile redis up` | H2 | ✅ | 分布式测试 |
| `docker-compose --profile full up` | MySQL | ✅ | 生产部署 |

## ⚙️ 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `DB_URL` | `jdbc:h2...` | 数据库连接 (推荐在 start.sh 中配置) |
| `DB_USERNAME` | `sa` | 数据库用户名 |
| `DB_PASSWORD` | (空) | 数据库密码 |
| `DB_DRIVER` | `org.h2.Driver` | 数据库驱动 (MySQL需设为 `com.mysql.cj.jdbc.Driver`) |
| `REDIS_HOST` | `localhost` | Redis 地址 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `REDIS_PASSWORD` | (空) | Redis 密码 |
| `DISTRIBUTION_MODE` | `async` | 分发模式 (`async`/`redis`) |
| `INGEST_MODE` | `sync` | 摄入模式 (`sync`/`redis`) |
| `INGEST_STREAM_KEY` | `webhook:events:ingest` | Redis 摄入流 Key |
| `ADMIN_PASSWORD` | (无默认) | 初始管理员密码 (仅首次有效，必须显式设置) |
| `WS_ALLOWED_ORIGINS` | `http://localhost:8080,...` | WebSocket 允许来源 (逗号分隔) |
| `SSRF_BLOCKED_IPS` | `127.0.0.1,...` | SSRF 禁止访问的 IP/CIDR 列表 |
| `H2_CONSOLE_ENABLED` | `false` | 是否启用 H2 Console |

## 📂 路径说明

| 路径 | 说明 |
|------|------|
| `/hooks/{source}/**` | Webhook 接收端点 |
| `/` | Dashboard 仪表盘 |
| `/subscriptions` | 订阅管理 |
| `/settings` | 系统设置 |
| `/monitoring` | 实时监控 |
| `/tunnel/connect` | WebSocket 隧道连接点 (Header: `X-Tunnel-Key`) |
| `/api/tunnel/**` | 隧道管理 API |

## 🛠 技术栈

- **后端**: Spring Boot 3.2.1, Spring Security, Spring Data JPA
- **消息**: Redis Stream (可选)
- **数据库**: H2 (默认) / MySQL 8.0
- **前端**: Thymeleaf + Tailwind CSS
- **监控**: Spring Boot Actuator + Prometheus

## 📄 开源协议

本项目采用 [MIT License](LICENSE) 开源协议。

---
**HookGateway** - 让 Webhook 管理变得简单、透明。
