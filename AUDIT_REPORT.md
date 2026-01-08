# HookGateway 安全审计报告

日期：2026-01-07  
范围：全仓库（主应用 + tunnel-agent）  
方法：静态审计（未做运行时验证与依赖漏洞库扫描）

## 总结
总体风险：**中**  
主要原因：公开摄入入口缺少速率/配额控制、敏感数据明文存储、SSRF 仍为黑名单策略。

## 发现的问题

### 高
1) /hooks 缺少速率与配额控制
- 风险：高频请求可压垮应用并导致存储膨胀。
- 证据：
  - /hooks 公开（`SecurityConfig`）
  - 单次大小限制仅 2MB（`RequestSizeLimitFilter`）
  - 清理默认关闭（`CleanupSchedulerService`）
- 建议：
  - 在反向代理/WAF 增加限流、并发与配额。
  - 生产环境启用清理策略并设置留存周期。

### 中
2) SSRF 仍为黑名单策略
- 风险：黑名单策略存在绕过面。
- 证据：
  - `UrlValidator` 仍以黑名单为主。
- 建议：
  - 改为 allowlist，限制目标域名/IP。

3) 敏感数据明文存储
- 风险：密钥/请求体泄露风险高。
- 证据：
  - `Subscription.verifySecret` 明文入库
  - `WebhookEvent.headers/payload` 明文入库
- 建议：
  - 密钥加密存储（KMS/信封加密）。
  - 请求体/头做脱敏或按需存储。

### 低
4) 前端 CDN 供应链风险
- 风险：CDN 被污染可能注入恶意脚本。
- 建议：
  - 自托管或增加 SRI + CSP。

5) docker-compose 开发配置默认启用 H2 Console
- 风险：被误用于生产。
- 建议：
  - 生产环境禁用 H2 Console。

## 已修复项（相对旧版本）
- 管理端表单已补齐 CSRF token。  
- 微信支付验签增加时间戳/nonce 防重放。  
- Tunnel ACK 已改为映射缺失时 fail closed。  
- 异步执行器与请求超时已配置。  
- Tunnel agent 默认使用 `wss://`。  

## 其他观察
- 测试覆盖较少，缺乏端到端回归。  
- 未做依赖漏洞扫描。  

## 加固建议（简版）
1) /hooks 增加限流与配额。  
2) 生产启用清理策略并设定留存期。  
3) SSRF 改为 allowlist。  
4) 密钥与敏感数据加密/脱敏。  
5) 前端资源自托管或启用 CSP/SRI。  

## 证据清单（文件索引）
- docker-compose.yml  
- src/main/java/com/example/hookgateway/config/SecurityConfig.java  
- src/main/java/com/example/hookgateway/filter/RequestSizeLimitFilter.java  
- src/main/java/com/example/hookgateway/service/CleanupSchedulerService.java  
- src/main/java/com/example/hookgateway/utils/UrlValidator.java  
- src/main/java/com/example/hookgateway/model/Subscription.java  
- src/main/java/com/example/hookgateway/model/WebhookEvent.java  
- src/main/resources/templates/*.html  
