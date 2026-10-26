package com.ucamp.app.domain.shop.service;

import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.shop.ShopImageFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3ShopImageObjectStorageTest {

    @Mock
    private S3Client client;

    private S3ShopImageObjectStorage storage() {
        return new S3ShopImageObjectStorage(client, "test-bucket", "shop-images");
    }

    @Test
    @DisplayName("검증된 파일을 지정한 버킷·prefix·Content-Type으로 공개 ACL 없이 저장한다")
    void put_validImage_setsObjectMetadataAndContent() throws IOException {
        var file = ShopImageFixture.image("png");
        ShopImageStorage storage = new ShopImageStorage(storage());
        String name = storage.save(file);
        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
        verify(client).putObject(request.capture(), body.capture());
        assertThat(request.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(request.getValue().key()).isEqualTo("shop-images/" + name);
        assertThat(request.getValue().contentType()).isEqualTo("image/png");
        assertThat(request.getValue().contentLength()).isEqualTo(file.getSize());
        assertThat(request.getValue().aclAsString()).isNull();
        try (var input = body.getValue().contentStreamProvider().newStream()) {
            assertThat(input.readAllBytes()).isEqualTo(file.getBytes());
        }
    }

    @Test
    @DisplayName("비공개 객체를 SDK로 읽고 삭제할 때 같은 버킷과 키를 사용한다")
    void readAndDelete_existingObject_usesConfiguredKey() {
        byte[] content = {1, 2, 3};
        when(client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(
                ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), content));
        assertThat(storage().read("image.png")).isEqualTo(content);
        storage().delete("image.png");
        verify(client).getObjectAsBytes(GetObjectRequest.builder().bucket("test-bucket")
                .key("shop-images/image.png").build());
        verify(client).deleteObject(DeleteObjectRequest.builder().bucket("test-bucket")
                .key("shop-images/image.png").build());
    }

    @Test
    @DisplayName("S3 404는 리소스 없음, 접근 거부와 연결 실패는 내부 오류로 변환한다")
    void read_s3Failure_mapsToBusinessError() {
        when(client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(
                S3Exception.builder().statusCode(404).build(),
                S3Exception.builder().statusCode(403).build(), SdkClientException.create("Connection failed"));
        assertError(() -> storage().read("image.png"), ErrorCode.RESOURCE_NOT_FOUND);
        assertError(() -> storage().read("image.png"), ErrorCode.INTERNAL_SERVER_ERROR);
        assertError(() -> storage().read("image.png"), ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("버킷 미설정은 자격 증명 조회나 외부 요청 전에 명확히 실패한다")
    void access_missingBucket_doesNotCallAws() {
        var storage = new S3ShopImageObjectStorage(client, "", "shop-images/");
        assertError(() -> storage.put("image.png", new byte[] {1}, "image/png"), ErrorCode.INTERNAL_SERVER_ERROR);
        assertError(() -> storage.read("image.png"), ErrorCode.INTERNAL_SERVER_ERROR);
        assertError(() -> storage.delete("image.png"), ErrorCode.INTERNAL_SERVER_ERROR);
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("이미지 검증 실패 시 S3를 호출하지 않는다")
    void upload_invalidImage_doesNotCallAws() {
        assertError(() -> new ShopImageStorage(storage()).save(null), ErrorCode.INVALID_INPUT);
        verifyNoInteractions(client);
    }

    private void assertError(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
