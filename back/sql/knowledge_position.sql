-- Run once on existing MySQL installations.
ALTER TABLE `interview_record`
    ADD COLUMN `position_code` VARCHAR(40) NOT NULL DEFAULT 'GENERAL'
    COMMENT '岗位知识包编码'
    AFTER `interview_mode`;
