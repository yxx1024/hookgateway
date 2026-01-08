package com.example.hookgateway.service;

import com.example.hookgateway.model.User;
import com.example.hookgateway.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 从数据库加载用户信息的 UserDetailsService 实现
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final com.example.hookgateway.security.LoginAttemptService loginAttemptService;
    private final jakarta.servlet.http.HttpServletRequest request;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // V17: 暴力破解防护
        String ip = getClientIP();
        if (loginAttemptService.isBlocked(ip)) {
            throw new org.springframework.security.authentication.LockedException(
                    "IP blocked due to too many failed login attempts");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                user.isEnabled(),
                true, // 账号未过期
                true, // 凭证未过期
                true, // 账号未锁定（当前由 IP 维度控制，必要时可改为 false 统一提示）
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private String getClientIP() {
        return request.getRemoteAddr();
    }
}
