package com.ucamp.app.domain.shop;

import java.sql.DriverManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ShopImageCleanupMigrationTest {

    @Test
    @DisplayName("기존 DB 적용 SQL이 H2 Oracle 모드에서 독립적으로 실행된다")
    void migration_h2OracleMode_createsCleanupQueue() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:image-cleanup-migration;MODE=Oracle")) {
            ScriptUtils.executeSqlScript(connection,
                    new FileSystemResource("../docs/erd/migrations/UNNIE-46-shop-image-cleanup.sql"));
            try (var statement = connection.createStatement();
                    var rows = statement.executeQuery("SELECT COUNT(*) FROM SHOP_IMAGE_CLEANUP")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isZero();
            }
        }
    }
}
