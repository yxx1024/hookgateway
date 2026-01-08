package com.example.hookgateway.config;

import com.example.hookgateway.security.ForcePasswordChangeFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 配置
 * 
 * 安全规则:
 * ✅ 开放：/hooks/** (Webhook 摄入端点)
 * ✅ 开放：/login, /css/**, /js/**, /webjars/**
 * ✅ 开放：/actuator/** (监控端点)
 * 🔒 保护：/, /subscriptions, /settings, /monitoring, /view/** 等管理页面
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final ForcePasswordChangeFilter forcePasswordChangeFilter;

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                http
                                // 禁用 CSRF 对 Webhook 端点和 Actuator（第三方平台/Prometheus 无法携带 CSRF Token）
                                .csrf(csrf -> csrf
                                                .ignoringRequestMatchers("/hooks/**", "/actuator/**"))
                                .authorizeHttpRequests(auth -> auth
                                                // 开放 Webhook 摄入端点和 Tunnel WebSocket 端点
                                                .requestMatchers("/hooks/**", "/tunnel/**").permitAll()
                                                // 开放静态资源和登录页
                                                .requestMatchers("/login", "/css/**", "/js/**", "/webjars/**",
                                                                "/favicon.ico", "/error")
                                                .permitAll()
                                                // Actuator 端点需要认证（生产环境安全）
                                                .requestMatchers("/actuator/**").hasRole("ADMIN")
                                                // 其他所有请求需要认证
                                                .anyRequest().authenticated())
                                .formLogin(form -> form
                                                .loginPage("/login")
                                                .loginProcessingUrl("/login")
                                                .defaultSuccessUrl("/", true)
                                                .failureHandler((request, response, exception) -> {
                                                        String error = "true";
                                                        // 判断是否为 LockedException（可能被包在
                                                        // InternalAuthenticationServiceException 里，也可能直接抛出）
                                                        org.slf4j.LoggerFactory.getLogger(SecurityConfig.class).info(
                                                                        "[LoginFailure] Exception type: {}, Cause: {}",
                                                                        exception.getClass().getName(),
                                                                        exception.getCause() != null ? exception
                                                                                        .getCause().getClass().getName()
                                                                                        : "null");

                                                        if (exception instanceof org.springframework.security.authentication.LockedException) {
                                                                error = "locked";
                                                        } else if (exception instanceof org.springframework.security.authentication.InternalAuthenticationServiceException) {
                                                                Throwable cause = exception.getCause();
                                                                if (cause instanceof org.springframework.security.authentication.LockedException) {
                                                                        error = "locked";
                                                                }
                                                        }
                                                        org.slf4j.LoggerFactory.getLogger(SecurityConfig.class).info(
                                                                        "[LoginFailure] Redirecting to /login?error={}",
                                                                        error);
                                                        response.sendRedirect("/login?error=" + error);
                                                })
                                                .permitAll())
                                .logout(logout -> logout
                                                .logoutUrl("/logout")
                                                .logoutSuccessUrl("/login?logout=true")
                                                .permitAll())
                                .addFilterAfter(forcePasswordChangeFilter,
                                                org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class);

                return http.build();
        }
}
