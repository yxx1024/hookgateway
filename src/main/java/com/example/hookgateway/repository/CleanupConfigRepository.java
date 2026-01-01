package com.example.hookgateway.repository;

import com.example.hookgateway.model.CleanupConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CleanupConfigRepository extends JpaRepository<CleanupConfig, Long> {
}
