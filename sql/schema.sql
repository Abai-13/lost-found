-- 失物招领平台 数据库初始化脚本
-- 数据库名称: lost_found (application.yml 中配置了 createDatabaseIfNotExist=true，会自动创建)

CREATE TABLE IF NOT EXISTS `user` (
    `id`          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    `username`    VARCHAR(50)  NOT NULL UNIQUE COMMENT '用户名',
    `password`    VARCHAR(255) NOT NULL COMMENT 'BCrypt加密后的密码',
    `nickname`    VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
    `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    `avatar_url`  VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    `role`        VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色: USER/ADMIN',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS `item` (
    `id`          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    `user_id`     BIGINT       NOT NULL COMMENT '发布者用户ID',
    `title`       VARCHAR(100) NOT NULL COMMENT '物品标题',
    `type`        VARCHAR(10)  NOT NULL COMMENT '类型: LOST(寻物启事) / FOUND(失物招领)',
    `category`    VARCHAR(30)  NOT NULL COMMENT '类别: 电子产品/证件/衣物/书籍/其他',
    `location`    VARCHAR(200) DEFAULT NULL COMMENT '丢失/拾获地点',
    `description` TEXT         DEFAULT NULL COMMENT '详细描述',
    `image_url`   VARCHAR(500) DEFAULT NULL COMMENT '物品图片URL',
    `contact`     VARCHAR(100) DEFAULT NULL COMMENT '联系方式',
    `status`      VARCHAR(20)  NOT NULL DEFAULT 'UNCLAIMED' COMMENT '状态: UNCLAIMED(未认领) / CLAIMED(已认领)',
    `version`     INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_user_id`   (`user_id`),
    INDEX `idx_type`      (`type`),
    INDEX `idx_category`  (`category`),
    INDEX `idx_status`    (`status`),
    INDEX `idx_created_at`(`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物品表';
