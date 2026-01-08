package com.example.hookgateway.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 网关用户实体，用于登录认证。
 * 存储于同一数据库（H2 或 MySQL），与现有配置完全兼容。
 */
@Entity
@Table(name = "gateway_users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    private String password; // BCrypt 编码后的密码

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private boolean passwordChanged = false;

    public User(String username, String password) {
        this.username = username;
        this.password = password;
        this.enabled = true;
    }
}
