package com.ucamp.app.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.auth.client.NtsBusinessClient;
import com.ucamp.app.domain.auth.dto.request.AuthBusinessVerificationRequest;
import com.ucamp.app.domain.auth.repository.ShopAccountRepository;

class AuthBusinessVerificationServiceTest {

    private final ShopAccountRepository repository = mock(ShopAccountRepository.class);
    private final NtsBusinessClient client = mock(NtsBusinessClient.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-02T00:00:00Z"), ZoneOffset.ofHours(9));
    private final AuthBusinessVerificationService service = new AuthBusinessVerificationService(repository, client, clock);

    @Test
    @DisplayName("형식과 체크섬이 틀린 번호는 외부 API를 호출하지 않는다")
    void verify_invalidNumber_doesNotCallProvider() {
        for (String number : new String[]{"1234567890", "0000000000", "123-45-67891", "abcdefghij"}) {
            assertThatThrownBy(() -> service.verify(request(number, LocalDate.of(2020, 1, 1))))
                    .isInstanceOf(BusinessException.class);
        }
        verifyNoInteractions(repository, client);
    }

    @Test
    @DisplayName("이미 등록된 사업자번호는 국세청 호출 전에 거절한다")
    void verify_duplicateNumber_throwsConflict() {
        when(repository.existsByBusinessNumber("1234567891")).thenReturn(true);
        assertThatThrownBy(() -> service.verify(request("1234567891", LocalDate.of(2020, 1, 1))))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.AUTH_DUPLICATE_BUSINESS_NUMBER));
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("Clock 기준 미래 개업일은 조회하지 않는다")
    void verify_futureDate_throwsException() {
        assertThatThrownBy(() -> service.verify(request("1234567891", LocalDate.of(2026, 11, 3))))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("유효한 새 사업자 정보는 국세청 진위확인을 호출한다")
    void verify_validInput_callsProvider() {
        service.verify(request("1234567891", LocalDate.of(2020, 1, 1)));
        verify(client).validate("1234567891", "테스트 대표", LocalDate.of(2020, 1, 1));
    }

    private static AuthBusinessVerificationRequest request(String number, LocalDate openedOn) {
        return new AuthBusinessVerificationRequest(number, "테스트 대표", openedOn);
    }
}
