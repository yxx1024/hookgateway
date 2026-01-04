-- Webhook Gateway MySQL 初始化脚本
-- 适用版本: V11 (支持 Webhook Tunneling)
-- 数据库名: webhook (请确保已创建该数据库)

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for subscription
-- ----------------------------
DROP TABLE IF EXISTS `subscription`;
CREATE TABLE `subscription` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL DEFAULT b'1',
  `created_at` datetime(6) DEFAULT NULL,
  `filter_type` varchar(255) DEFAULT 'NONE',
  `filter_rule` text,
  `source` varchar(255) NOT NULL,
  `target_url` varchar(255) NOT NULL,
  -- V10 预留安全验证字段 (根据 TODO.md)
  `verify_method` varchar(50) DEFAULT 'NONE',
  `verify_secret` text,
  `signature_header` varchar(100) DEFAULT NULL,
  -- V11 Webhook Tunneling 支持
  `destination_type` varchar(20) DEFAULT 'HTTP' COMMENT '目标类型: HTTP, TUNNEL',
  `tunnel_key` varchar(100) DEFAULT NULL COMMENT 'Tunnel 认证密钥 (UUID)',
  PRIMARY KEY (`id`),
  KEY `idx_tunnel_key` (`tunnel_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for webhook_event
-- ----------------------------
DROP TABLE IF EXISTS `webhook_event`;
CREATE TABLE `webhook_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `delivery_count` int DEFAULT '0',
  `delivery_details` text,
  `headers` text,
  `last_delivery_at` datetime(6) DEFAULT NULL,
  `method` varchar(255) DEFAULT NULL,
  `payload` text,
  `received_at` datetime(6) DEFAULT NULL,
  `source` varchar(255) DEFAULT NULL,
  `status` varchar(255) DEFAULT 'RECEIVED',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for cleanup_config
-- ----------------------------
DROP TABLE IF EXISTS `cleanup_config`;
CREATE TABLE `cleanup_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enabled` bit(1) DEFAULT b'0',
  `last_cleanup_count` bigint DEFAULT '0',
  `last_run_at` datetime(6) DEFAULT NULL,
  `retention_days` int DEFAULT '30',
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- 插入初始配置
-- ----------------------------
INSERT INTO `cleanup_config` (`enabled`, `retention_days`, `updated_at`) VALUES (b'0', 30, NOW());

-- ----------------------------
-- Table structure for gateway_users (V10 登录管控)
-- ----------------------------
DROP TABLE IF EXISTS `gateway_users`;
CREATE TABLE `gateway_users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL,
  `password` varchar(100) NOT NULL,
  `enabled` bit(1) NOT NULL DEFAULT b'1',
  `password_changed` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_gateway_users_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- 默认管理员账户: 已移除默认插入，请通过 DataInitializer 或手动插入，并强制修改密码
-- ----------------------------
-- INSERT INTO `gateway_users` (`username`, `password`, `enabled`, `password_changed`) 
-- VALUES ('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMy.MqrqXrM3nKWLHGM3GqCXKzQjQMQQqye', b'1', b'0');

SET FOREIGN_KEY_CHECKS = 1;
