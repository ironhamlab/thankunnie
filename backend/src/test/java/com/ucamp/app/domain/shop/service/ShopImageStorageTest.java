package com.ucamp.app.domain.shop.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.shop.ShopImageFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class ShopImageStorageTest {

    @TempDir
    Path directory;

    private ShopImageStorage storage;

    @BeforeEach
    void setUp() {
        storage = new ShopImageStorage(new LocalShopImageObjectStorage(directory.toString()),
                mock(ShopImageCleanupService.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"png", "jpg"})
    @DisplayName("정상 JPEG와 PNG를 저장하고 원래 바이너리와 타입으로 조회한다")
    void save_validImage_roundTrips(String format) throws IOException {
        MockMultipartFile file = ShopImageFixture.image(format);
        String name = storage.save(file);
        assertThat(name).isNotEqualTo(file.getOriginalFilename());
        assertThat(storage.read(name).content()).isEqualTo(file.getBytes());
        assertThat(storage.read(name).contentType()).isEqualTo(file.getContentType());
    }

    @Test
    @DisplayName("누락·빈 파일·확장자·MIME·위장·손상 파일을 저장 전에 거부한다")
    void save_invalidFiles_leavesDirectoryEmpty() throws IOException {
        byte[] png = ShopImageFixture.image("png").getBytes();
        MockMultipartFile[] files = {
                null,
                new MockMultipartFile("file", "image.png", "image/png", new byte[0]),
                new MockMultipartFile("file", "image.svg", "image/svg+xml", png),
                new MockMultipartFile("file", "image.png", "application/octet-stream", png),
                new MockMultipartFile("file", "image.jpg", "image/jpeg", png),
                new MockMultipartFile("file", "image.png", "image/png", "fake image".getBytes()),
                new MockMultipartFile("file", "image.png", "image/png", Arrays.copyOf(png, 35))
        };
        for (MockMultipartFile file : files) {
            assertInvalid(() -> storage.save(file));
        }
        assertThat(directory).isEmptyDirectory();
    }

    @Test
    @DisplayName("5MB는 허용하고 1바이트 초과는 거부한다")
    void save_sizeBoundary_acceptsOnlyWithinLimit() throws IOException {
        byte[] png = ShopImageFixture.image("png").getBytes();
        String name = storage.save(new MockMultipartFile("file", "image.png", "image/png",
                Arrays.copyOf(png, ShopImageStorage.MAX_BYTES)));
        assertThat(Files.size(directory.resolve(name))).isEqualTo(ShopImageStorage.MAX_BYTES);
        assertInvalid(() -> storage.save(new MockMultipartFile("file", "image.png", "image/png",
                new byte[ShopImageStorage.MAX_BYTES + 1])));
    }

    @Test
    @DisplayName("압축 파일이 작아도 과도한 이미지 해상도는 디코딩 전에 거부한다")
    void save_largeDimensions_rejectsBeforeDecode() throws IOException {
        byte[] png = ShopImageFixture.image("png").getBytes();
        ByteBuffer.wrap(png).putInt(16, 100_000).putInt(20, 100_000);
        assertInvalid(() -> storage.save(new MockMultipartFile("file", "image.png", "image/png", png)));
        assertThat(directory).isEmptyDirectory();
    }

    @Test
    @DisplayName("사용자 파일명으로 저장 경로를 조작할 수 없고 임의 파일을 조회·삭제할 수 없다")
    void access_pathTraversal_cannotEscapeDirectory() throws IOException {
        byte[] png = ShopImageFixture.image("png").getBytes();
        String name = storage.save(new MockMultipartFile("file", "../../image.png", "image/png", png));
        assertThat(directory.resolve(name).getParent()).isEqualTo(directory);
        Path sentinel = Files.writeString(directory.resolve("keep.txt"), "keep");
        storage.deleteQuietly("keep.txt");
        storage.deleteQuietly("../keep.txt");
        assertThat(sentinel).hasContent("keep");
        assertThatThrownBy(() -> storage.read("../keep.txt"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("저장 디렉터리를 만들 수 없으면 시스템 오류를 반환한다")
    void save_storageFailure_throwsInternalError() throws IOException {
        Path blockedDirectory = Files.writeString(directory.resolve("blocked"), "file");
        ShopImageStorage blockedStorage = new ShopImageStorage(
                new LocalShopImageObjectStorage(blockedDirectory.toString()), mock(ShopImageCleanupService.class));
        MockMultipartFile file = ShopImageFixture.image("png");
        assertThatThrownBy(() -> blockedStorage.save(file)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR));
        assertThat(blockedDirectory).hasContent("file");
    }

    @Test
    @DisplayName("S3 삭제와 재시도 기록용 DB가 함께 실패해도 이미 결정된 이미지 변경 결과를 뒤집지 않는다")
    void cleanup_storageAndQueueFailure_doesNotPropagate() {
        ShopImageObjectStorage objectStorage = mock(ShopImageObjectStorage.class);
        ShopImageCleanupService cleanup = mock(ShopImageCleanupService.class);
        String fileName = "12345678-1234-1234-1234-123456789012.png";
        when(objectStorage.scope()).thenReturn("s3:test-bucket/shop-images/");
        doThrow(new IllegalStateException("Simulated S3 failure")).when(objectStorage).delete(fileName);
        doThrow(new IllegalStateException("Simulated database failure"))
                .when(cleanup).enqueue("s3:test-bucket/shop-images/", fileName);
        assertThatCode(() -> new ShopImageStorage(objectStorage, cleanup).deleteQuietly(fileName))
                .doesNotThrowAnyException();
        verify(cleanup).enqueue("s3:test-bucket/shop-images/", fileName);
    }

    private void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
    }
}
