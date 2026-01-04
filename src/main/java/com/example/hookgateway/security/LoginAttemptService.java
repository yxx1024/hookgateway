package com.example.hookgateway.security;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Service to track login attempts and block IPs to prevent brute force attacks.
 * Policy: 5 failed attempts locks the IP for 15 minutes.
 */
@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long BLOCK_DURATION_MS = TimeUnit.MINUTES.toMillis(15);

    // Stores IP -> FailedAttempt
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

            // If the last failure was a long time ago (longer than block duration),
            // reset the counter. (Sliding window-ish behavior)
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
        // Check if lock has expired
        if (now - attempt.lastFailureTime > BLOCK_DURATION_MS) {
            // It's expired, but we don't clean it up here (lazy cleanup on next write or
            // dedicated cleaner)
            // Functionally it is not blocked.
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
