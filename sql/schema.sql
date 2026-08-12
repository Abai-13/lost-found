-- 校园失物招领平台 — 数据库初始化脚本
-- MySQL 8.0 容器首次启动时自动执行

CREATE TABLE IF NOT EXISTS `user` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '用户ID',
    `username`   VARCHAR(50)  NOT NULL                 COMMENT '登录用户名',
    `password`   VARCHAR(255) NOT NULL                 COMMENT 'BCrypt 加密密码',
    `nickname`   VARCHAR(50)  DEFAULT NULL             COMMENT '显示昵称',
    `phone`      VARCHAR(20)  DEFAULT NULL             COMMENT '手机号',
    `avatar_url` VARCHAR(500) DEFAULT NULL             COMMENT '头像URL',
    `role`       VARCHAR(20)  NOT NULL DEFAULT 'USER'  COMMENT '角色: USER/ADMIN',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS `item` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '物品ID',
    `user_id`     BIGINT       NOT NULL                 COMMENT '发布者ID',
    `title`       VARCHAR(100) NOT NULL                 COMMENT '物品标题',
    `type`        VARCHAR(10)  NOT NULL                 COMMENT 'LOST: 寻物 / FOUND: 招领',
    `category`    VARCHAR(30)  NOT NULL                 COMMENT '类别: 电子产品/证件/衣物/书籍/其他',
    `location`    VARCHAR(200) DEFAULT NULL             COMMENT '丢失/拾获地点',
    `description` TEXT         DEFAULT NULL             COMMENT '详细描述',
    `image_url`   VARCHAR(500) DEFAULT NULL             COMMENT '图片URL',
    `contact`     VARCHAR(100) DEFAULT NULL             COMMENT '联系方式',
    `status`      VARCHAR(20)  NOT NULL DEFAULT 'UNCLAIMED' COMMENT 'UNCLAIMED / CLAIMED',
    `version`     INT          NOT NULL DEFAULT 0       COMMENT '乐观锁版本号',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_type` (`type`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物品表';
