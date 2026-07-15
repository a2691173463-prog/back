CREATE TABLE IF NOT EXISTS `resume_draft` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '所属用户',
    `template_id` BIGINT NULL COMMENT '使用的模板',
    `title` VARCHAR(128) NOT NULL COMMENT '草稿标题',
    `content_json` LONGTEXT NOT NULL COMMENT '结构化简历 JSON',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0 草稿，1 已完成',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_resume_draft_user_update` (`user_id`, `update_time`),
    KEY `idx_resume_draft_template` (`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='在线简历草稿表';
