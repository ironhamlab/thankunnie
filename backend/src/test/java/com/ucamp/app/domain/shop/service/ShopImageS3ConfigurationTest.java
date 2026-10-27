package com.ucamp.app.domain.shop.service;

import java.net.URI;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ShopImageS3ConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ShopImageCleanupService.class, () -> mock(ShopImageCleanupService.class))
            .withUserConfiguration(ShopImageS3Configuration.class, S3ShopImageObjectStorage.class,
                    LocalShopImageObjectStorage.class, ShopImageStorage.class);

    @Test
    @DisplayName("키·버킷 설정 없이 기본 S3 빈을 생성하고 외부 연결 없이 기동한다")
    void start_withoutCredentials_createsS3Storage() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(S3Client.class);
            assertThat(context.getBean(ShopImageObjectStorage.class)).isInstanceOf(S3ShopImageObjectStorage.class);
        });
    }

    @Test
    @DisplayName("local을 선택하면 AWS 클라이언트 없이 로컬 저장소만 생성한다")
    void start_local_doesNotCreateS3Client() {
        contextRunner.withPropertyValues("app.shop-image.storage=local").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(S3Client.class);
            assertThat(context.getBean(ShopImageObjectStorage.class)).isInstanceOf(LocalShopImageObjectStorage.class);
        });
    }

    @Test
    @DisplayName("시스템 속성의 HTTP S3 엔드포인트 재정의는 기동 시 거부한다")
    void start_httpEndpoint_rejectsOverride() {
        contextRunner.withSystemProperties("aws.endpointUrl=http://localhost:9000").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class)
                    .hasStackTraceContaining("S3 엔드포인트 재정의는 HTTPS만 허용합니다.");
        });
    }

    @Test
    @DisplayName("HTTPS S3 엔드포인트 재정의는 원래 URI를 유지한다")
    void start_httpsEndpoint_keepsOverride() {
        contextRunner.withSystemProperties("aws.endpointUrl=https://s3.example.test").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(S3Client.class).serviceClientConfiguration().endpointOverride())
                    .contains(URI.create("https://s3.example.test"));
        });
    }
}
