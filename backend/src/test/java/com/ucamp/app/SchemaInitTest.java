package com.ucamp.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SchemaInitTest {

    private static final int TABLE_COUNT = 13;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("H2 시작 시 docs/erd/01_schema_ddl.sql 로 모든 테이블이 생성된다")
    void schemaInit_createsAllTables() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'", Integer.class);

        assertThat(tableCount).isEqualTo(TABLE_COUNT);
    }

    @Test
    @DisplayName("시퀀스로 PK 값을 발급할 수 있다")
    void schemaInit_createsSequences() {
        Long first = jdbcTemplate.queryForObject("SELECT SEQ_RESERVATION.NEXTVAL FROM DUAL", Long.class);
        Long second = jdbcTemplate.queryForObject("SELECT SEQ_RESERVATION.NEXTVAL FROM DUAL", Long.class);

        assertThat(second).isEqualTo(first + 1);
    }
}
