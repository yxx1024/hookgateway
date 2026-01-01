-- Webhook Tunneling 功能数据库迁移脚本
-- 适用于已有数据库的增量更新
-- 执行前请确保备份数据

-- 为 subscription 表增加 Tunnel 支持字段
ALTER TABLE `subscription` 
ADD COLUMN `destination_type` VARCHAR(20) DEFAULT 'HTTP' COMMENT '目标类型: HTTP, TUNNEL',
ADD COLUMN `tunnel_key` VARCHAR(100) DEFAULT NULL COMMENT 'Tunnel 认证密钥 (UUID)';

-- 为现有数据设置默认值
UPDATE `subscription` SET `destination_type` = 'HTTP' WHERE `destination_type` IS NULL;

-- 添加索引以提高查询性能
CREATE INDEX idx_tunnel_key ON `subscription`(`tunnel_key`);

-- 验证
SELECT id, source, destination_type, tunnel_key FROM `subscription` LIMIT 5;
