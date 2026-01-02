# HookGateway - High-Performance Webhook Routing & Management Gateway

<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2.1-brightgreen.svg" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Java-17-blue.svg" alt="Java 17">
  <img src="https://img.shields.io/badge/Docker-Ready-2496ED.svg" alt="Docker">
  <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License">
</p>

<p align="center">
  <a href="./README.md">中文</a> | <strong>English</strong>
</p>

## 🚀 Introduction

**HookGateway** is a lightweight, scalable Webhook routing and management platform. It is designed to solve the pain points of Webhook integration in heterogeneous systems, providing core capabilities such as ingestion storage, multi-channel distribution, exponential backoff retries, content-level filtering, and real-time monitoring.

## ✨ Core Features

| Feature | Description |
|---------|-------------|
| 📥 **Heterogeneous Ingestion** | Unified reception of Webhook requests from different sources |
| 🛡️ **Security Verification** | HMAC-SHA256 / WeChat Pay RSA-SHA256 |
| 🔍 **Content Filtering** | JSONPath & Regex rules |
| 🔃 **One-Click Replay** | Replay via URL or Tunnel with one click |
| 🔁 **Smart Retry** | Full-link Exponential Backoff + Attempt Tracking |
| 🚇 **Tunnel Mode** | WebSocket Tunneling with Distributed Broadcast support |
| 🐳 **Docker Support** | One-click deployment, H2/MySQL/Redis optional |

## 🌟 Use Cases

### 1. Unified Webhook Entry & Distribution
When multiple heterogeneous services (e.g., GitHub, Stripe, WeChat Pay) need to notify internal systems, HookGateway acts as a unified entry point to receive, process, and distribute them to multiple backend microservices.
* **Example**: GitHub Push event -> CI Build Service + Slack Notification Bot + Audit Log Service.

### 2. Enhanced Webhook Reliability
Third-party platforms often have limited retry mechanisms. HookGateway provides persistent storage and exponential backoff retries, ensuring that messages are not lost even if internal consumer services are temporarily down, and are automatically replayed after recovery.

### 3. Security Verification Gateway
Sink complex signature verification logic (such as WeChat Pay's RSA-SHA256, GitHub's HMAC) to the gateway layer. Internal services do not need to repeat the verification code, focusing only on business logic, and internal network communication is safer.

### 4. Fine-grained Routing & Filtering
Don't need to process all events? Use JSONPath or Regex rules to forward only key events (e.g., `status == 'paid'`) to core services, filtering out irrelevant noise (e.g., `ping` or `read` events) to reduce downstream pressure.

## 🏁 Quick Start

### Option 1: Docker (Recommended)

```bash
# Standalone Deployment (H2 In-Memory Database, no MySQL/Redis required)
docker-compose up hookgateway

# Full Deployment (MySQL + Redis)
docker-compose --profile full up
```

### Option 2: Run Locally

```bash
# Use the startup script (automatically releases ports)
./start.sh

# Or run manually
mvn clean package -DskipTests
java -jar target/hookgateway-0.0.1-SNAPSHOT.jar
```

### Access

- **Dashboard**: http://localhost:8080
- **H2 Console**: http://localhost:8080/h2-console (User: `sa`, Password: empty)
- **Default Login**: `admin` / `admin123` (Password change required on first login)

## 🐳 Docker Deployment Options

| Command | Database | Redis | Scenario |
|---------|----------|-------|----------|
| `docker-compose up hookgateway` | H2 Memory | ❌ | Dev/Demo |
| `docker-compose --profile mysql up` | MySQL | ❌ | Standalone Prod |
| `docker-compose --profile redis up` | H2 | ✅ | Distributed Test |
| `docker-compose --profile full up` | MySQL | ✅ | Production |

## ⚙️ Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_URL` | `jdbc:h2:mem:webhookdb` | Database Connection URL |
| `DB_USERNAME` | `sa` | Database Username |
| `DB_PASSWORD` | (empty) | Database Password |
| `REDIS_HOST` | `localhost` | Redis Host |
| `DISTRIBUTION_MODE` | `async` | Distribution Mode (`async`/`redis`) |

## 📂 Path Description

| Path | Description |
|------|-------------|
| `/hooks/{source}/**` | Webhook Ingestion Endpoint |
| `/` | Dashboard |
| `/subscriptions` | Subscription Management |
| `/settings` | System Settings |
| `/monitoring` | Real-time Monitoring |

## 🛠 Tech Stack

- **Backend**: Spring Boot 3.2.1, Spring Security, Spring Data JPA
- **Messaging**: Redis Stream (Optional)
- **Database**: H2 (Default) / MySQL 8.0
- **Frontend**: Thymeleaf + Tailwind CSS
- **Monitoring**: Spring Boot Actuator + Prometheus

## 📄 License

This project is licensed under the [MIT License](LICENSE).

---
**HookGateway** - Making Webhook management simple and transparent.
