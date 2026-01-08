package com.example.hookgateway.repository;

import com.example.hookgateway.model.CleanupConfig;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 清理配置仓储接口。
 */
public interface CleanupConfigRepository extends JpaRepository<CleanupConfig, Long> {
}
