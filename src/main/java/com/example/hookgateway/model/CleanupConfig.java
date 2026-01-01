package com.example.hookgateway.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CleanupConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 保留天数：7/10/15/30
     */
    @Builder.Default
    private Integer retentionDays = 30;

    /**
     * 是否启用自动清理
     */
    @Builder.Default
    private Boolean enabled = false;

    /**
     * 上次执行时间
     */
    private LocalDateTime lastRunAt;

    /**
     * 上次清理的记录数
     */
    @Builder.Default
    private Long lastCleanupCount = 0L;

    /**
     * 配置更新时间
     */
    private LocalDateTime updatedAt;
}
