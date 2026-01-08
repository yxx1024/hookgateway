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
            // 修复：强制要求设置 ADMIN_PASSWORD
            String initPassword = System.getenv("ADMIN_PASSWORD");

            if (initPassword == null || initPassword.isEmpty()) {
                log.error("❌ 严重错误: 未设置 ADMIN_PASSWORD 环境变量。");
                log.error("❌ 出于安全考虑，不再自动生成随机密码。请设置环境变量 ADMIN_PASSWORD 后重启应用。");
                // 可选：System.exit(1) 直接退出，但对类库场景过于激进
                // 对独立应用可接受。当前选择不创建用户，以安全方式阻断系统使用。
                return;
            }

            User admin = new User(
                    DEFAULT_ADMIN_USERNAME,
                    passwordEncoder.encode(initPassword));
            userRepository.save(admin);

            log.info("✅ 已创建默认管理员账户: {} (使用 ADMIN_PASSWORD 环境变量)", DEFAULT_ADMIN_USERNAME);
        } else {
            // 检查是否已修改初始密码
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
