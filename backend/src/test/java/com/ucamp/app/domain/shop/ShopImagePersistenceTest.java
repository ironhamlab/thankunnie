package com.ucamp.app.domain.shop;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.TestTransaction;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.config.TimeConfig;
import com.ucamp.app.domain.shop.service.ShopImageService;
import com.ucamp.app.domain.shop.service.ShopImageStorage;
import com.ucamp.app.domain.shop.service.LocalShopImageObjectStorage;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ShopImageService.class, ShopImageStorage.class, LocalShopImageObjectStorage.class, TimeConfig.class})
@ActiveProfiles("test")
class ShopImagePersistenceTest {

    @TempDir
    static Path directory;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.shop-image.directory", () -> directory.toString());
    }

    @Autowired
    private ShopImageService service;
    @Autowired
    private ShopImageStorage storage;
    @Autowired
    private JdbcTemplate jdbc;

    private final AuthUser owner = new AuthUser(9601L, UserRole.OWNER, 9601L);
    private String previousName;
    private String previousUrl;

    @BeforeEach
    void setUp() throws IOException {
        previousName = storage.save(ShopImageFixture.image("png"));
        previousUrl = "/api/v1/shops/9601/images/" + previousName;
        jdbc.update("INSERT INTO CATEGORY (CATEGORY_ID, NAME) VALUES (9601, '테스트')");
        LocalDateTime time = LocalDateTime.of(2026, 10, 26, 12, 0);
        jdbc.update("""
                INSERT INTO SHOP (SHOP_ID, CATEGORY_ID, LOGIN_ID, PASSWORD_HASH, NAME, REGION_CODE,
                    ADDRESS, LATITUDE, LONGITUDE, PHONE, STATUS, CREATED_AT, UPDATED_AT, IMAGE_URL)
                VALUES (9601, 9601, 'CHANGE_ME', 'CHANGE_ME', '매장', '11680',
                    '서울', 37.5, 127.0, '02-000-0000', 'ACTIVE', ?, ?, ?)
                """, time, time, previousUrl);
        // 기존 이미지가 커밋된 상태에서 교체·삭제 트랜잭션을 시작한다.
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
    }

    @AfterEach
    void cleanUp() {
        if (TestTransaction.isActive()) {
            TestTransaction.flagForRollback();
            TestTransaction.end();
        }
        jdbc.update("DELETE FROM SHOP WHERE SHOP_ID = 9601");
        jdbc.update("DELETE FROM CATEGORY WHERE CATEGORY_ID = 9601");
    }

    @Test
    @DisplayName("이미지 교체 커밋 전에는 기존 파일을 유지하고 커밋 후 새 URL과 파일만 남긴다")
    void upload_commit_replacesImageAfterCommit() throws IOException {
        var result = service.uploadImage(owner, 9601L, ShopImageFixture.image("jpg"));
        assertThat(directory.resolve(previousName)).exists();
        TestTransaction.flagForCommit();
        TestTransaction.end();
        assertThat(imageUrl()).isEqualTo(result.imageUrl());
        assertThat(directory.resolve(previousName)).doesNotExist();
        assertThat(service.getImage(9601L, fileName(result.imageUrl())).contentType()).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("이미지 교체가 DB에서 롤백되면 기존 URL·파일을 유지하고 새 파일을 제거한다")
    void upload_rollback_keepsOriginalAndRemovesNewFile() throws IOException {
        var result = service.uploadImage(owner, 9601L, ShopImageFixture.image("jpg"));
        TestTransaction.flagForRollback();
        TestTransaction.end();
        assertThat(imageUrl()).isEqualTo(previousUrl);
        assertThat(directory.resolve(previousName)).exists();
        assertThat(directory.resolve(fileName(result.imageUrl()))).doesNotExist();
    }

    @Test
    @DisplayName("이미지 삭제가 롤백되면 기존 URL과 파일이 남는다")
    void delete_rollback_keepsOriginal() {
        service.deleteImage(owner, 9601L);
        TestTransaction.flagForRollback();
        TestTransaction.end();
        assertThat(imageUrl()).isEqualTo(previousUrl);
        assertThat(directory.resolve(previousName)).exists();
    }

    @Test
    @DisplayName("이미지 삭제 커밋 후 URL과 파일을 지우고 재삭제도 성공한다")
    void delete_commit_clearsImageAndIsIdempotent() {
        service.deleteImage(owner, 9601L);
        assertThat(directory.resolve(previousName)).exists();
        TestTransaction.flagForCommit();
        TestTransaction.end();
        assertThat(imageUrl()).isNull();
        assertThat(directory.resolve(previousName)).doesNotExist();
        service.deleteImage(owner, 9601L);
        assertThat(imageUrl()).isNull();
    }

    private String imageUrl() {
        return jdbc.queryForObject("SELECT IMAGE_URL FROM SHOP WHERE SHOP_ID = 9601", String.class);
    }

    private String fileName(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }
}
