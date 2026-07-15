CREATE TABLE IF NOT EXISTS `user_profile` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `skill_name` VARCHAR(100) NOT NULL COMMENT '技能名称',
    `score` DECIMAL(3,2) NOT NULL DEFAULT 1.00 COMMENT '能力星级分数 (1.00 - 5.00)',
    `comment` VARCHAR(512) DEFAULT NULL COMMENT '技能掌握情况评语与薄弱点',
    `source_interview_id` BIGINT DEFAULT NULL COMMENT '评分来源的面试记录ID',
    `last_update` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_user_skill` (`user_id`, `skill_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户能力技能画像表';
