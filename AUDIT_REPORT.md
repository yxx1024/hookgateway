# HookGateway 安全审计报告

日期：2025-02-14  
范围：全仓库（主应用 + tunnel-agent）  
方法：静态审计（未做运行时验证与依赖漏洞库扫描）

## 总结
总体风险：**高**  
主要原因：默认管理员账号/口令、管理端表单缺失 CSRF、验签重放防护不足。

## 发现的问题
### 高
2) CSRF 开启但多数管理端 POST 表单未带 CSRF token
- 风险：容易被开发者“为修复报错而关闭 CSRF”，从而引入真实 CSRF 漏洞。
- 证据：
  - 仅对 /hooks/** 与 /actuator/** 关闭 CSRF
  - 登录/改密/订阅管理表单均缺少 CSRF 字段
- 文件：
  - src/main/java/com/example/hookgateway/config/SecurityConfig.java
  - src/main/resources/templates/login.html
  - src/main/resources/templates/change-password.html
  - src/main/resources/templates/subscriptions.html
- 建议：
  - 所有 POST 表单加隐藏 CSRF 字段。
  - 保持全局 CSRF 开启。

### 中
3) 微信支付验签缺少时间戳/nonce 新鲜度校验
- 风险：旧请求可被重放，触发重复业务。
- 证据：
  - WechatPayVerifier 仅校验签名
- 文件：
  - src/main/java/com/example/hookgateway/security/WechatPayVerifier.java
- 建议：
  - 限制时间戳窗口（如 5 分钟）并缓存 nonce 防重放。

4) Tunnel ACK 授权仅依赖 Redis 映射
- 风险：Redis 不可用时，ACK 可更新任意 eventId 的状态与日志。
- 证据：
  - Redis 不可用时 getTunnelKeyForEvent 返回 null
- 文件：
  - src/main/java/com/example/hookgateway/websocket/TunnelWebSocketHandler.java
  - src/main/java/com/example/hookgateway/websocket/TunnelSessionManager.java
- 建议：
  - 在事件表持久化 tunnelKey 或加本地 TTL 映射兜底。

### 中
5) 高压下缺乏背压与超时控制
- 风险：异步任务与正则过滤可能拖垮线程池；出站请求无整体超时。
- 证据：
  - @Async 使用默认执行器
  - regex 使用公共线程池 + 手工超时
  - HttpClient 仅设置 connectTimeout
- 文件：
  - src/main/java/com/example/hookgateway/controller/IngestController.java
  - src/main/java/com/example/hookgateway/service/ReplayService.java
- 建议：
  - 配置有界线程池与队列。
  - 为出站请求增加 request timeout。
  - 正则过滤采用更安全的引擎或预校验。

### 低
6) SSRF 仅黑名单校验且未限制协议
- 风险：存在边界绕过（IPv6、协议混淆等）。
- 证据：
  - URL 校验仅做黑名单 + DNS 解析
- 文件：
  - src/main/java/com/example/hookgateway/utils/UrlValidator.java
- 建议：
  - 仅允许 http/https，补齐 IPv6/保留地址拦截，最好改为 allowlist。

7) 敏感数据明文存储/日志输出
- 风险：密钥与 payload 可能泄露。
- 证据：
  - verifySecret 明文入库
  - payload/headers 明文入库
  - 生成的管理员密码写入日志
- 文件：
  - src/main/java/com/example/hookgateway/model/Subscription.java
  - src/main/java/com/example/hookgateway/model/WebhookEvent.java
  - src/main/java/com/example/hookgateway/config/DataInitializer.java
- 建议：
  - 对密钥与敏感字段加密或脱敏，避免日志打印密码。

## 其他观察
- 测试覆盖较少（仅见 WeChatPay 验签测试）。
- docker-compose 默认开启 H2 Console，不适合生产环境。

## 加固建议（简版）
1) 移除默认账号/口令与 SQL 初始化，启动时强制显式设置管理员密码。  
2) 所有管理端 POST 表单补齐 CSRF token。  
3) 微信支付增加时间戳/nonce 防重放。  
4) 异步处理与出站请求增加有界执行器与超时控制。  
5) SSRF 校验改为协议白名单 + 更严格 IP 规则。

## 证据清单（文件索引）
- init_mysql.sql
- README.md
- docker-compose.yml
- src/main/java/com/example/hookgateway/config/SecurityConfig.java
- src/main/java/com/example/hookgateway/security/WechatPayVerifier.java
- src/main/java/com/example/hookgateway/websocket/TunnelWebSocketHandler.java
- src/main/java/com/example/hookgateway/websocket/TunnelSessionManager.java
- src/main/java/com/example/hookgateway/controller/IngestController.java
- src/main/java/com/example/hookgateway/service/ReplayService.java
- src/main/java/com/example/hookgateway/utils/UrlValidator.java
- src/main/java/com/example/hookgateway/model/Subscription.java
- src/main/java/com/example/hookgateway/model/WebhookEvent.java
- src/main/java/com/example/hookgateway/config/DataInitializer.java
- src/main/resources/templates/login.html
- src/main/resources/templates/change-password.html
- src/main/resources/templates/subscriptions.html
