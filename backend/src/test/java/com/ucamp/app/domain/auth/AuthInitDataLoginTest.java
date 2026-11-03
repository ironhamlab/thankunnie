package com.ucamp.app.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.ucamp.app.common.auth.TestUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.domain.auth.dto.request.AuthLoginRequest;
import com.ucamp.app.domain.auth.dto.response.AuthTokenResponse;
import com.ucamp.app.domain.auth.service.AuthService;

// local 초기 데이터의 테스트 계정이 실제 로그인으로도 X-Test-User 와 같은 사용자가 되는지 확인한다
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.sql.init.data-locations=classpath:init-data.sql")
class AuthInitDataLoginTest {

    // docs/erd/02_init_data_dml.sql 의 로컬·시연 전용 테스트 비밀번호
    private static final String TEST_PASSWORD = "test1234!";

    @Autowired
    private AuthService authService;

    @Test
    @DisplayName("고객·관리자 테스트 계정으로 로그인한다")
    void loginCustomer_initDataAccounts() {
        assertThat(authService.loginCustomer(new AuthLoginRequest("customer1", TEST_PASSWORD)))
                .satisfies(token -> assertSameUser(token, TestUser.CUSTOMER_1));
        AuthTokenResponse admin = authService.loginCustomer(new AuthLoginRequest("admin", TEST_PASSWORD));
        assertThat(admin.role()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.userId()).isEqualTo(TestUser.ADMIN.toAuthUser().userId());
    }

    @Test
    @DisplayName("매장 테스트 계정으로 로그인하면 같은 매장 ID 를 받는다")
    void loginShop_initDataAccounts() {
        AuthTokenResponse owner = authService.loginShop(new AuthLoginRequest("owner1", TEST_PASSWORD));

        assertThat(owner.role()).isEqualTo(UserRole.OWNER);
        assertThat(owner.shopId()).isEqualTo(TestUser.OWNER_1.toAuthUser().shopId());
    }

    private static void assertSameUser(AuthTokenResponse token, TestUser testUser) {
        assertThat(token.userId()).isEqualTo(testUser.toAuthUser().userId());
        assertThat(token.role()).isEqualTo(testUser.toAuthUser().role());
        assertThat(token.shopId()).isEqualTo(testUser.toAuthUser().shopId());
    }
}
