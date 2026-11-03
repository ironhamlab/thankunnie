package com.ucamp.app.common.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;

class AuthUserTest {

    private final AuthUser customer = TestUser.CUSTOMER_1.toAuthUser();
    private final AuthUser owner = TestUser.OWNER_1.toAuthUser();
    private final AuthUser admin = TestUser.ADMIN.toAuthUser();

    @Test
    @DisplayName("허용된 역할이면 통과한다")
    void requireRole_allowedRole_passes() {
        assertThatCode(() -> admin.requireRole(UserRole.OWNER, UserRole.ADMIN)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("허용되지 않은 역할이면 FORBIDDEN 예외가 발생한다")
    void requireRole_otherRole_throwsForbidden() {
        assertThatThrownBy(() -> customer.requireRole(UserRole.OWNER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("본인 매장 계정이면 통과한다")
    void requireShopOwner_ownShop_passes() {
        assertThatCode(() -> owner.requireShopOwner(owner.shopId())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("다른 매장 계정이면 FORBIDDEN 예외가 발생한다")
    void requireShopOwner_otherShop_throwsForbidden() {
        Long otherShopId = TestUser.OWNER_2.toAuthUser().shopId();

        assertThatThrownBy(() -> owner.requireShopOwner(otherShopId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("매장이 아니면 매장 ID 와 관계없이 FORBIDDEN 예외가 발생한다")
    void requireShopOwner_notOwner_throwsForbidden() {
        assertThatThrownBy(() -> admin.requireShopOwner(owner.shopId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("매장 ID 가 null 이면 매장이어도 FORBIDDEN 예외가 발생한다")
    void requireShopOwner_nullShopId_throwsForbidden() {
        assertThatThrownBy(() -> owner.requireShopOwner(null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("매장 ID 가 없는 매장은 생성할 수 없다")
    void create_ownerWithoutShop_throwsException() {
        assertThatThrownBy(() -> new AuthUser(99L, UserRole.OWNER, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("헤더 값은 대소문자와 앞뒤 공백을 무시하고 테스트 사용자로 변환한다")
    void testUserFrom_caseInsensitive_returnsTestUser() {
        assertThat(TestUser.from(" owner_1 ")).contains(TestUser.OWNER_1);
        assertThat(TestUser.from("UNKNOWN")).isEmpty();
        assertThat(TestUser.from(null)).isEmpty();
    }
}
