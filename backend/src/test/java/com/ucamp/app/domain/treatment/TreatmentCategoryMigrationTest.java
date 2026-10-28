package com.ucamp.app.domain.treatment;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class TreatmentCategoryMigrationTest {

    @Test
    @DisplayName("카테고리 제거 SQL은 H2 Oracle 모드에서 시술·예약·매장 카테고리를 보존한다")
    void migration_existingCategory_preservesTreatmentAndReservation() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:treatment-category-migration;MODE=Oracle")) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("../docs/erd/01_schema_ddl.sql"));
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("../docs/erd/02_init_data_dml.sql"));
            // 과거 UNNIE-32를 적용한 DB를 재현한다.
            ScriptUtils.executeSqlScript(connection,
                    new FileSystemResource("../docs/erd/migrations/UNNIE-32-treatment-category.sql"));
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE TREATMENT SET CATEGORY_ID = 2 WHERE TREATMENT_ID = 1");
                ScriptUtils.executeSqlScript(connection,
                        new FileSystemResource("../docs/erd/migrations/UNNIE-111-remove-treatment-category.sql"));
                try (var columns = connection.getMetaData().getColumns(null, "PUBLIC", "TREATMENT", "CATEGORY_ID")) {
                    assertThat(columns.next()).isFalse();
                }
                try (var rows = statement.executeQuery("""
                        SELECT s.CATEGORY_ID, t.PRICE, r.APPLIED_PRICE, r.DEPOSIT
                        FROM TREATMENT t JOIN SHOP s ON s.SHOP_ID = t.SHOP_ID
                        JOIN RESERVATION r ON r.TREATMENT_ID = t.TREATMENT_ID
                        WHERE r.RESERVATION_ID = 1
                        """)) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong("CATEGORY_ID")).isEqualTo(1L);
                    assertThat(rows.getInt("PRICE")).isEqualTo(30000);
                    assertThat(rows.getInt("APPLIED_PRICE")).isEqualTo(30000);
                    assertThat(rows.getInt("DEPOSIT")).isEqualTo(20000);
                }
            }
        }
    }
}
