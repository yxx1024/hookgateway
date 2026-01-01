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
                                                .requestMatchers("/login", "/css/**", "/js/**", "/webjars/**", "/error")
                                                .permitAll()
                                                // Actuator 端点需要认证（生产环境安全）
                                                .requestMatchers("/actuator/**").authenticated()
                                                // 其他所有请求需要认证
                                                .anyRequest().authenticated())
                                .formLogin(form -> form
                                                .loginPage("/login")
                                                .loginProcessingUrl("/login")
                                                .defaultSuccessUrl("/", true)
                                                .failureUrl("/login?error=true")
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
