package com.example.hookgateway.repository;

import com.example.hookgateway.model.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface WebhookEventRepository
                extends JpaRepository<WebhookEvent, Long>, JpaSpecificationExecutor<WebhookEvent> {

        /**
         * Delete all events received before the specified date
         * 
         * @param cutoffDate the cutoff date
         * @return number of deleted records
         */
        @Modifying
        @Query("DELETE FROM WebhookEvent w WHERE w.receivedAt < :cutoffDate")
        int deleteByReceivedAtBefore(LocalDateTime cutoffDate);

        /**
         * Count events by status
         * 
         * @param status the status to count
         * @return number of events with the specified status
         */
        long countByStatus(String status);
}
