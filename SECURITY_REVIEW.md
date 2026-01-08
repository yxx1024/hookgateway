# HookGateway 安全审计报告

日期：2026-01-07  
范围：主应用 + tunnel-agent  
方法：静态代码审查（未做运行时测试、依赖漏洞库扫描）  
CLAUDE.md：未找到  

## 总结
总体风险：中

主要原因：
- `/hooks/**` 公开入口缺少速率/配额/并发限制，仍可被流量打满并触发存储增长。  
- 订阅验签密钥与请求头/请求体明文存储。  
- SSRF 防护仍为黑名单策略，存在绕过可能。  

## 发现的问题

### 高-1：/hooks 缺少速率与配额控制
影响：  
即使单次请求体有 2MB 限制，仍可通过高频请求压垮应用或导致数据库持续膨胀，形成 DoS 或成本风险。

证据：  
- `src/main/java/com/example/hookgateway/config/SecurityConfig.java`（/hooks 公开）  
- `src/main/java/com/example/hookgateway/filter/RequestSizeLimitFilter.java`（仅 2MB 单次限制）  
- `src/main/java/com/example/hookgateway/model/WebhookEvent.java`（headers/payload 明文存储）  
- `src/main/java/com/example/hookgateway/service/CleanupSchedulerService.java`（清理默认关闭）  

建议：  
- 在反向代理/WAF 层对 `/hooks/**` 做限流、并发与配额控制。  
- 生产环境默认开启清理策略，并明确留存周期。  
- 视业务裁剪或分级存储 payload。  

### 中-1：SSRF 防护仍为黑名单策略
影响：  
黑名单与 DNS 固定策略仍可能被部分 IPv6 或域名策略绕过，存在内网访问风险。

证据：  
- `src/main/java/com/example/hookgateway/utils/UrlValidator.java`  
- `src/main/java/com/example/hookgateway/service/ReplayService.java`  

建议：  
- 改为严格 allowlist（域名/IP 允许列表）。  
- 强化对非公网地址的全量拦截（含 IPv6）。  

### 中-2：敏感数据明文存储
影响：  
数据库泄露会暴露验签密钥、请求头和 payload，存在敏感信息外泄风险。

证据：  
- `src/main/java/com/example/hookgateway/model/Subscription.java`（verifySecret）  
- `src/main/java/com/example/hookgateway/model/WebhookEvent.java`（headers/payload）  

建议：  
- 对密钥做加密存储（信封加密/外部 KMS）。  
- 对 payload/headers 做脱敏或按需存储。  

### 低-1：UI 依赖 CDN 资源存在供应链风险
影响：  
CDN 被污染可能向管理页面注入恶意脚本。

证据：  
- `src/main/resources/templates/dashboard.html`（其它模板同样引用）  

建议：  
- 自托管静态资源或增加 SRI + CSP。  

### 低-2：开发环境默认开启 H2 Console
影响：  
`docker-compose` 的开发配置默认开启 H2 Console，容易被误用于生产。

证据：  
- `docker-compose.yml`（hookgateway 服务启用 H2_CONSOLE_ENABLED=true）  

建议：  
- 生产环境明确禁用 H2 Console。  

## 已修复/已缓解项
- 已增加请求体大小过滤（2MB）。  
- Tunnel ACK 在映射缺失时已 fail closed。  
- 微信支付验签加入时间戳/nonce 防重放。  
- 管理端表单已补齐 CSRF token。  
- Tunnel agent 默认改为 `wss://`。  

## 备注
- 未执行运行时测试或依赖漏洞库扫描。  
- 建议在 CI 中加入依赖漏洞扫描与安全基线检查。  

## 优先行动建议（最小改动、收益最高）
1) 给 `/hooks/**` 增加限流与配额控制（反向代理/WAF）。  
2) 明确数据留存策略并启用清理。  
3) SSRF 改为 allowlist 策略。  
4) 密钥/敏感字段做加密或脱敏。  
5) 管理端资源自托管或加 CSP/SRI。  
