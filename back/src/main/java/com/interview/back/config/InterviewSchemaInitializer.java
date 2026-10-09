package com.interview.back.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class InterviewSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public InterviewSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!columnExists("position_code")) {
            jdbcTemplate.execute(
                    "ALTER TABLE interview_record "
                            + "ADD COLUMN position_code VARCHAR(40) NOT NULL DEFAULT 'GENERAL' "
                            + "COMMENT '岗位知识包编码' AFTER interview_mode"
            );
        }
        if (!columnExists("evaluation_skills")) {
            jdbcTemplate.execute(
                    "ALTER TABLE interview_record "
                            + "ADD COLUMN evaluation_skills JSON DEFAULT NULL "
                            + "COMMENT '按有效作答证据生成的分项评分' AFTER evaluation"
            );
        }
    }

    private boolean columnExists(String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'interview_record'
                  AND COLUMN_NAME = ?
                """,
                Integer.class,
                columnName
        );
        return count != null && count > 0;
    }
}
