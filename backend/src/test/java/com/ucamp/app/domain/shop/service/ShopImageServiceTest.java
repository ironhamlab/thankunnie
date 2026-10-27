package com.ucamp.app.domain.shop.service;

import java.io.IOException;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.shop.ShopImageFixture;
import com.ucamp.app.domain.shop.entity.Shop;
import com.ucamp.app.domain.shop.repository.ShopRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopImageServiceTest {

    @Mock
    private ShopRepository repository;
    @Mock
    private ShopImageStorage storage;
    @Mock
    private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @InjectMocks
    private ShopImageService service;

    private final AuthUser owner = new AuthUser(11L, UserRole.OWNER, 1L);
    private MockMultipartFile file;

    @BeforeEach
    void setUp() throws IOException {
        file = ShopImageFixture.image("png");
    }

    @Test
    @DisplayName("다른 매장 사장님·고객·관리자의 업로드와 삭제는 파일·DB 접근 전에 거부한다")
    void mutate_nonOwner_throwsForbidden() {
        for (AuthUser user : new AuthUser[] {new AuthUser(12L, UserRole.OWNER, 2L),
                new AuthUser(1L, UserRole.CUSTOMER, null), new AuthUser(21L, UserRole.ADMIN, null)}) {
            assertError(() -> service.uploadImage(user, 1L, file), ErrorCode.FORBIDDEN);
            assertError(() -> service.deleteImage(user, 1L), ErrorCode.FORBIDDEN);
        }
        verifyNoInteractions(repository, storage);
    }

    @Test
    @DisplayName("본인 매장이 없으면 파일 저장 전에 404를 반환한다")
    void upload_missingShop_throwsNotFound() {
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.empty());
        assertError(() -> service.uploadImage(owner, 1L, file), ErrorCode.RESOURCE_NOT_FOUND);
        assertError(() -> service.deleteImage(owner, 1L), ErrorCode.RESOURCE_NOT_FOUND);
        verifyNoInteractions(storage);
    }

    @Test
    @DisplayName("검증 실패와 저장 실패 시 기존 이미지 URL을 변경하지 않는다")
    void upload_storageFailure_keepsPreviousImage() {
        when(repository.existsById(1L)).thenReturn(true);
        when(storage.save(file)).thenThrow(new BusinessException(ErrorCode.INVALID_INPUT),
                new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
        for (ErrorCode error : new ErrorCode[] {ErrorCode.INVALID_INPUT, ErrorCode.INTERNAL_SERVER_ERROR}) {
            assertError(() -> service.uploadImage(owner, 1L, file), error);
        }
        verify(repository, never()).findByIdForUpdate(1L);
        verify(storage, never()).deleteQuietly(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("현재 대표 이미지가 아닌 URL은 파일 접근 전에 404로 처리한다")
    void getImage_staleUrl_throwsNotFound() {
        Shop shop = mock(Shop.class);
        when(storage.isStoredFileName("old.png")).thenReturn(true);
        when(repository.findById(1L)).thenReturn(Optional.of(shop));
        when(shop.getImageUrl()).thenReturn("/api/v1/shops/1/images/new.png");
        assertError(() -> service.getImage(1L, "old.png"), ErrorCode.RESOURCE_NOT_FOUND);
        verify(storage, never()).read("old.png");
    }

    @Test
    @DisplayName("트랜잭션 시작 실패 시 업로드한 파일을 정리한다")
    void upload_transactionBeginFailure_removesNewFile() {
        when(repository.existsById(1L)).thenReturn(true);
        when(storage.save(file)).thenReturn("new.png");
        var failure = new org.springframework.transaction.CannotCreateTransactionException("test");
        when(transactionManager.getTransaction(org.mockito.ArgumentMatchers.any())).thenThrow(failure);
        assertThatThrownBy(() -> service.uploadImage(owner, 1L, file)).isSameAs(failure);
        verify(storage).deleteQuietly("new.png");
        verify(repository, never()).findByIdForUpdate(1L);
    }

    private void assertError(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
