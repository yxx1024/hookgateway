package com.example.hookgateway.security;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 记录登录失败并按 IP 封禁，防止暴力破解。
 * 策略：5 次失败封禁 15 分钟。
 */
@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long BLOCK_DURATION_MS = TimeUnit.MINUTES.toMillis(15);

    // 记录 IP -> 失败次数
    private final Map<String, FailedAttempt> attemptsCache = new ConcurrentHashMap<>();

    /**
     * 登录成功后清理失败记录。
     *
     * @param key IP 地址
     */
    public void loginSucceeded(String key) {
        attemptsCache.remove(key);
    }

    /**
     * 记录一次登录失败。
     *
     * @param key IP 地址
     */
    public void loginFailed(String key) {
        attemptsCache.compute(key, (k, attempt) -> {
            long now = System.currentTimeMillis();
            if (attempt == null) {
                return new FailedAttempt(1, now);
            }

            // 若上次失败距今超过封禁时长，则重置计数（类滑动窗口）
            if (now - attempt.lastFailureTime > BLOCK_DURATION_MS) {
                return new FailedAttempt(1, now);
            }

            attempt.count++;
            attempt.lastFailureTime = now;
            return attempt;
        });
    }

    /**
     * 判断 IP 是否被封禁。
     *
     * @param key IP 地址
     * @return true 表示被封禁
     */
    public boolean isBlocked(String key) {
        FailedAttempt attempt = attemptsCache.get(key);
        if (attempt == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        // 检查封禁是否过期
        if (now - attempt.lastFailureTime > BLOCK_DURATION_MS) {
            // 已过期但不立即清理，功能上视为未封禁
            return false;
        }

        return attempt.count >= MAX_ATTEMPTS;
    }

    private static class FailedAttempt {
        int count;
        long lastFailureTime;

        public FailedAttempt(int count, long lastFailureTime) {
            this.count = count;
            this.lastFailureTime = lastFailureTime;
        }
    }

    /**
     * 监听认证成功事件，清理失败记录。
     *
     * @param event 认证事件
     */
    @org.springframework.context.event.EventListener
    public void onAuthenticationSuccess(
            org.springframework.security.authentication.event.AuthenticationSuccessEvent event) {
        String ip = getClientIP(event.getAuthentication());
        if (ip != null) {
            loginSucceeded(ip);
        }
    }

    /**
     * 监听认证失败事件，增加失败计数。
     *
     * @param event 认证事件
     */
    @org.springframework.context.event.EventListener
    public void onAuthenticationFailure(
            org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent event) {
        String ip = getClientIP(event.getAuthentication());
        if (ip != null) {
            loginFailed(ip);
        }
    }

    /**
     * 从认证详情中获取客户端 IP。
     *
     * @param authentication 认证信息
     * @return IP 地址
     */
    private String getClientIP(org.springframework.security.core.Authentication authentication) {
        if (authentication == null)
            return null;
        Object details = authentication.getDetails();
        if (details instanceof org.springframework.security.web.authentication.WebAuthenticationDetails) {
            return ((org.springframework.security.web.authentication.WebAuthenticationDetails) details)
                    .getRemoteAddress();
        }
        return null;
    }
}
