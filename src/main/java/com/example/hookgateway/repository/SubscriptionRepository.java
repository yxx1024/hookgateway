package com.example.hookgateway.repository;

import com.example.hookgateway.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 订阅仓储接口。
 */
@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    /**
     * 查询指定来源的启用订阅。
     *
     * @param source 来源
     * @return 订阅列表
     */
    List<Subscription> findBySourceAndActiveTrue(String source);

    /**
     * 根据隧道 Key 查询订阅。
     *
     * @param tunnelKey 隧道 Key
     * @return 订阅信息
     */
    java.util.Optional<Subscription> findByTunnelKey(String tunnelKey);
}
