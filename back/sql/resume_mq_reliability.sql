-- RabbitMQ reliability upgrade for an existing interview_db database.
CREATE TABLE IF NOT EXISTS `resume_parse_outbox` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `message_id` VARCHAR(64) NOT NULL COMMENT 'Globally unique message ID',
    `resume_id` BIGINT NOT NULL COMMENT 'Resume ID',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0 pending, 1 sent, 2 failed, 3 publishing',
    `retry_count` INT NOT NULL DEFAULT 0 COMMENT 'Publish retry count',
    `next_retry_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Next publish time',
    `last_error` VARCHAR(1000) DEFAULT NULL COMMENT 'Last publish error',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_resume_outbox_message_id` (`message_id`),
    KEY `idx_resume_outbox_pending` (`status`, `next_retry_time`),
    KEY `idx_resume_outbox_resume_id` (`resume_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Resume parse transactional outbox';

ALTER TABLE `resume`
    MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 0
    COMMENT '0 pending, 1 success, 2 failed, 3 processing';
