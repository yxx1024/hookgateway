# HookGateway 性能优化演进计划 (High Performance Roadmap)

本文档基于 V10 版本架构进行瓶颈分析，并提供针对千万级日流量场景的优化方案。

---

## �️ 兼容性原则 (Critical)
**HookGateway 的核心优势是“开箱即用”。**
所有优化方案**必须**遵循以下原则，确保在未配置 MySQL/Redis 时，系统仍能以默认的 H2 + 内存队列模式正常运行。

*   **默认轻量 (H2 + Local)**: 不依赖任何外部组件，保留当前简单的同步读写模式。
*   **按需升级 (MySQL + Redis)**: 只有当用户显式配置了 MySQL 和 Redis 时，才激活高性能摄入与分发逻辑。
*   **实现手段**: 严格使用 `@ConditionalOnProperty` 和 `@Profile` 进行 Bean 加载控制。

---

## �📊 当前架构评估 (Baseline)

### 1. 核心瓶颈分析
| 环节 | 当前机制 | 理论极限 | 瓶颈原因 |
|------|----------|----------|----------|
| **摄入 (Ingest)** | 同步写库 (`INSERT`) | 500 ~ 2,000 QPS | 数据库磁盘 I/O 成为硬上限，流量洪峰直接压垮数据库。 |
| **持久化 (Store)** | MySQL / H2 | 取決于硬盘吞吐 | 关系型数据库在高并发写入下锁竞争严重。 |
| **分发 (Delivery)** | 串行 HTTP 请求 | (1000ms / 平均响应耗时) * 并发数 | `WebhookStreamConsumer` 内部串行处理所有订阅，单个慢响应会阻塞后续所有任务。 |

### 2. 适用场景
*   **适用**: 企业内部系统集成、中小规模 SaaS (日请求量 < 500w)。
*   **不适用**: 公有云高并发网关、直播弹幕类高频触发场景。

---

## 🚀 优化方案 A：异步缓冲写入 (Write-Behind) - 优先级 P0
**适用场景**: `app.distribution.mode=redis` 且配置了 MySQL。
**目标**: 将摄入 QPS 提升至 Redis 极限 (10w+)，彻底解耦数据库写压力。

### 改造步骤
1.  **修改 `IngestController` (条件分支)**:
    *   **IF (Mode == Redis)**: 取消同步 `eventRepository.save(event)`，直接推送到 Redis Stream，返回 `202 Accepted`。
    *   **ELSE (Mode == Async)**: 保持原有逻辑，同步写入 H2 数据库，返回 `200 OK`。

2.  **新增 `EventPersister` 服务**:
    *   启动独立线程组，批量 (`pipelining`) 从 Redis 拉取事件。
    *   使用 JDBC Batch Insert (`saveAll`) 批量写入 MySQL（如每 500 条写一次）。
    *   **优势**: 数据库写入次数减少 500 倍，极大降低 I/O 压力。

3.  **风险控制**:
    *   需处理 Redis 宕机导致的数据丢失风险 (通常可接受，或通过 AOF 规避)。

---

## ⚡ 优化方案 B：并行分发 (Parallel Delivery) - 优先级 P1
**目标**: 消除“队头阻塞”效应，确保慢速订阅者不影响核心业务。

### 改造步骤
1.  **引入虚拟线程 (Java 21+) 或线程池**:
    *   在 `WebhookStreamConsumer` 中，不再使用 `for (Subscription sub : subs)` 串行调用。
    *   改为提交到 `ExecutorService` 并行执行。

    ```java
    // 伪代码示例
    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
        for (Subscription sub : subs) {
            scope.fork(() -> replayService.replay(sub, event));
        }
        scope.join();
    }
    ```

2.  **超时隔离**:
    *   为不同优先级的订阅设置不同的 HTTP Client 超时时间。

---

## 🛠 优化方案 C：数据库读写分离 (Read/Write Splitting) - 优先级 P2
**目标**: 提升 Dashboard 查询速度，避免分发任务抢占查询资源。

### 改造步骤
1.  **主从配置**: 
    *   配置 MySQL 主从复制 (Master-Slave)。
2.  **代码分离**:
    *   `IngestController` (写) -> 连接 Master。
    *   `Dashboard / Reporting` (读) -> 连接 Slave。
3.  **技术选型**:
    *   使用 ShardingSphere-JDBC 或简单地配置多数据源。

---

## 🔮 终极架构 (Cloud Native)
如果流量达到亿级，建议完全重构：
1.  **摄入层**: Netty / Reactor (WebFlux) 纯异步网关。
2.  **消息队列**: Kafka (替代 Redis Stream)。
3.  **存储**: ClickHouse (存储海量日志) + MySQL (仅存元数据)。
