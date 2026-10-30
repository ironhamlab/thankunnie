package com.ucamp.app.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ucamp.app.common.auth.TestUser;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.user.dto.response.UserProfileResponse;
import com.ucamp.app.domain.user.entity.AppUser;
import com.ucamp.app.domain.user.entity.AppUserRole;
import com.ucamp.app.domain.user.repository.AppUserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("고객은 이름·휴대폰 번호·가입 시각을 조회한다")
    void getMyProfile_customer_returnsProfile() {
        AppUser user = AppUser.builder()
                .loginId("customer1")
                .passwordHash("hash")
                .name("고객일")
                .phone("010-1111-1111")
                .role(AppUserRole.USER)
                .build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2026, 10, 1, 9, 0));
        given(appUserRepository.findById(1L)).willReturn(Optional.of(user));

        UserProfileResponse response = userService.getMyProfile(TestUser.CUSTOMER_1.toAuthUser());

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("고객일");
        assertThat(response.phone()).isEqualTo("010-1111-1111");
        assertThat(response.createdAt()).isEqualTo(OffsetDateTime.parse("2026-10-01T09:00:00+09:00"));
    }

    @Test
    @DisplayName("사장님은 회원 테이블을 조회하지 않고 403 이다")
    void getMyProfile_owner_forbidden() {
        assertThatThrownBy(() -> userService.getMyProfile(TestUser.OWNER_1.toAuthUser()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
        verifyNoInteractions(appUserRepository);
    }

    @Test
    @DisplayName("회원 행이 없으면 404 이다")
    void getMyProfile_missingUser_notFound() {
        given(appUserRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMyProfile(TestUser.CUSTOMER_1.toAuthUser()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }
}
