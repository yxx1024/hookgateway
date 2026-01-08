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

    public void loginSucceeded(String key) {
        attemptsCache.remove(key);
    }

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

    @org.springframework.context.event.EventListener
    public void onAuthenticationSuccess(
            org.springframework.security.authentication.event.AuthenticationSuccessEvent event) {
        String ip = getClientIP(event.getAuthentication());
        if (ip != null) {
            loginSucceeded(ip);
        }
    }

    @org.springframework.context.event.EventListener
    public void onAuthenticationFailure(
            org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent event) {
        String ip = getClientIP(event.getAuthentication());
        if (ip != null) {
            loginFailed(ip);
        }
    }

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
