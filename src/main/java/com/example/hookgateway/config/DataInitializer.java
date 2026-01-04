package com.example.hookgateway.config;

import com.example.hookgateway.model.User;
import com.example.hookgateway.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 应用启动时初始化默认管理员账户
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String DEFAULT_ADMIN_USERNAME = "admin";

    @Override
    public void run(String... args) {
        if (!userRepository.existsByUsername(DEFAULT_ADMIN_USERNAME)) {
            // Fix: Read from env or generate random password
            String initPassword = System.getenv("ADMIN_INIT_PASSWORD");
            boolean randomGenerated = false;

            if (initPassword == null || initPassword.isEmpty()) {
                // Generate secure random password
                java.security.SecureRandom random = new java.security.SecureRandom();
                byte[] bytes = new byte[8];
                random.nextBytes(bytes);
                initPassword = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                randomGenerated = true;
            }

            User admin = new User(
                    DEFAULT_ADMIN_USERNAME,
                    passwordEncoder.encode(initPassword));
            userRepository.save(admin);

            if (randomGenerated) {
                log.info("✅ 已创建默认管理员账户: {} (密码随机生成)", DEFAULT_ADMIN_USERNAME);
                log.info("🔑 请立即保存管理员密码: {}", initPassword);
            } else {
                log.info("✅ 已创建默认管理员账户: {} (使用环境变量提供的密码)", DEFAULT_ADMIN_USERNAME);
            }
        } else {
            // Check if password has been changed
            userRepository.findByUsername(DEFAULT_ADMIN_USERNAME).ifPresent(user -> {
                if (!user.isPasswordChanged()) {
                    log.warn("⚠️ 警告: 默认管理员账户 ({}) 尚未修改初始密码！", DEFAULT_ADMIN_USERNAME);
                    log.warn("👉 请尽快登录并修改密码以确保系统安全。");
                } else {
                    log.info("✅ 管理员账户已存在且已修改默认密码，系统安全。");
                }
            });
        }
    }
}
