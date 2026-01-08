package com.example.hookgateway.repository;

import com.example.hookgateway.model.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * Webhook 事件仓储接口。
 */
@Repository
public interface WebhookEventRepository
                extends JpaRepository<WebhookEvent, Long>, JpaSpecificationExecutor<WebhookEvent> {

        /**
         * 删除指定时间之前的事件
         *
         * @param cutoffDate 截止时间
         * @return 删除记录数
         */
        @Modifying
        @Query("DELETE FROM WebhookEvent w WHERE w.receivedAt < :cutoffDate")
        int deleteByReceivedAtBefore(LocalDateTime cutoffDate);

        /**
         * 按状态统计事件数
         *
         * @param status 目标状态
         * @return 指定状态的事件数量
         */
        long countByStatus(String status);
}
