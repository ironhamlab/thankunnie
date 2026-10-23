package com.ucamp.app.domain.shop.service;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.shop.dto.request.ShopUpdateRequest;
import com.ucamp.app.domain.shop.entity.Shop;
import com.ucamp.app.domain.shop.repository.ShopCategoryRepository;
import com.ucamp.app.domain.shop.repository.ShopRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopServiceTest {

    @Mock
    private ShopRepository shopRepository;
    @Mock
    private ShopCategoryRepository categoryRepository;
    @InjectMocks
    private ShopService service;

    private final AuthUser owner = new AuthUser(11L, UserRole.OWNER, 1L);
    private final ShopUpdateRequest request = new ShopUpdateRequest(1L, "매장", "11680", "서울", null,
            new BigDecimal("37.5"), new BigDecimal("127.0"), "02-000-0000", null, null, null);

    @Test
    @DisplayName("타 매장 조회와 수정은 DB 접근 전에 차단한다")
    void access_otherShop_throwsForbidden() {
        assertForbidden(() -> service.getShop(owner, 2L));
        assertForbidden(() -> service.updateShop(owner, 2L, request));
        verifyNoInteractions(shopRepository, categoryRepository);
    }

    @Test
    @DisplayName("고객과 관리자는 매장을 수정할 수 없다")
    void update_nonOwner_throwsForbidden() {
        assertForbidden(() -> service.updateShop(new AuthUser(1L, UserRole.CUSTOMER, null), 1L, request));
        assertForbidden(() -> service.updateShop(new AuthUser(21L, UserRole.ADMIN, null), 1L, request));
        verifyNoInteractions(shopRepository, categoryRepository);
    }

    @Test
    @DisplayName("존재하지 않는 본인 매장은 404 업무 예외를 반환한다")
    void getShop_missing_throwsNotFound() {
        when(shopRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getShop(owner, 1L)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("유효한 카테고리이면 매장 프로필을 변경한다")
    void update_valid_updatesProfile() {
        Shop shop = mock(Shop.class);
        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop));
        when(categoryRepository.existsById(1L)).thenReturn(true);
        service.updateShop(owner, 1L, request);
        verify(shop).updateProfile(request);
    }

    @Test
    @DisplayName("없는 카테고리는 매장 변경 전에 거부한다")
    void update_missingCategory_throwsInvalidInput() {
        Shop shop = mock(Shop.class);
        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop));
        assertThatThrownBy(() -> service.updateShop(owner, 1L, request))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        verifyNoInteractions(shop);
    }

    private void assertForbidden(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }
}
