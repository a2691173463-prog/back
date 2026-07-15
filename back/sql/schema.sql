CREATE DATABASE IF NOT EXISTS interview_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE interview_db;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username` VARCHAR(64) NOT NULL COMMENT '用户名',
    `password` VARCHAR(128) NOT NULL COMMENT '密码',
    `role` VARCHAR(16) NOT NULL DEFAULT 'user' COMMENT '角色 user-普通用户 admin-管理员',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 简历分析表
CREATE TABLE IF NOT EXISTS `resume` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
    `file_name` VARCHAR(128) NOT NULL COMMENT '简历原文件名',
    `file_url` VARCHAR(512) NOT NULL COMMENT '简历文件存储路径',
    `parsed_content` TEXT COMMENT '解析后的纯文本',
    `diagnosis_result` TEXT COMMENT '大模型诊断建议(JSON)',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '处理状态 0-处理中 1-成功 2-失败',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简历记录表';

-- 模拟面试记录表
CREATE TABLE IF NOT EXISTS `interview_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
    `resume_id` BIGINT NOT NULL COMMENT '关联简历ID',
    `question_id` BIGINT DEFAULT NULL COMMENT '关联经典题目ID',
    `chat_history` JSON COMMENT '对话历史记录(可选存库，主要放Redis)',
    `score` INT DEFAULT NULL COMMENT '最终面试评分',
    `evaluation` TEXT DEFAULT NULL COMMENT '大模型面试评估报告',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态 0-进行中 1-已结束',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_resume_id` (`resume_id`),
    KEY `idx_question_id` (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟面试记录表';

-- 经典面试题库
CREATE TABLE IF NOT EXISTS `question` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `title` VARCHAR(256) NOT NULL COMMENT '题目标题',
    `category` VARCHAR(64) NOT NULL COMMENT '分类，如 Java, Spring, Redis, MySQL',
    `difficulty` VARCHAR(16) NOT NULL COMMENT '难度 Easy, Medium, Hard',
    `description` TEXT NOT NULL COMMENT '题目描述',
    `reference_answer` TEXT NOT NULL COMMENT '参考答案',
    `view_count` INT NOT NULL DEFAULT 0 COMMENT '浏览量',
    `interview_count` INT NOT NULL DEFAULT 0 COMMENT '发起模拟面试次数',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='经典面试题库';

-- 简历模板表
CREATE TABLE IF NOT EXISTS `resume_template` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name` VARCHAR(128) NOT NULL COMMENT '模板名称',
    `category` VARCHAR(64) NOT NULL COMMENT '分类，如 研发岗, PM岗, 设计岗',
    `thumbnail_url` VARCHAR(512) NOT NULL COMMENT '缩略图地址',
    `download_url` VARCHAR(512) NOT NULL COMMENT '模板文件下载地址',
    `description` VARCHAR(512) COMMENT '模板描述',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简历模板表';

