-- 双模式面试字段迁移。历史记录沿用旧系统行为，标记为专项训练模式。
ALTER TABLE `interview_record`
    ADD COLUMN `interview_mode` VARCHAR(16) NOT NULL DEFAULT 'standard'
    COMMENT '面试模式 standard-标准面试 practice-专项训练'
    AFTER `resume_id`;

UPDATE `interview_record`
SET `interview_mode` = 'practice';
