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
    private static final String DEFAULT_ADMIN_PASSWORD = "admin123";

    @Override
    public void run(String... args) {
        if (!userRepository.existsByUsername(DEFAULT_ADMIN_USERNAME)) {
            User admin = new User(
                    DEFAULT_ADMIN_USERNAME,
                    passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD));
            userRepository.save(admin);
            log.info("✅ 已创建默认管理员账户: {} / {}", DEFAULT_ADMIN_USERNAME, DEFAULT_ADMIN_PASSWORD);
        } else {
            log.info("ℹ️ 管理员账户已存在，跳过初始化");
        }
    }
}
