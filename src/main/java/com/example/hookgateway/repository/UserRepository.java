package com.example.hookgateway.repository;

import com.example.hookgateway.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户数据访问层
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 根据用户名查询用户。
     *
     * @param username 用户名
     * @return 用户信息
     */
    Optional<User> findByUsername(String username);

    /**
     * 判断用户名是否存在。
     *
     * @param username 用户名
     * @return true 表示存在
     */
    boolean existsByUsername(String username);
}
