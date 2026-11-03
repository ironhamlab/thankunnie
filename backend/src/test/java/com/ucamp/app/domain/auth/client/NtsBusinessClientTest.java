package com.ucamp.app.domain.auth.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;

import java.net.SocketTimeoutException;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;

class NtsBusinessClientTest {

    private static final LocalDate OPENED_ON = LocalDate.of(2020, 1, 1);
    private static final String NUMBER = "1234567891";
    private static final String URL = "https://api.odcloud.kr/api/nts-businessman/v1/validate"
            + "?serviceKey=CHANGE_ME_TEST%2Bkey%2F%3D&returnType=JSON";
    private MockRestServiceServer server;
    private NtsBusinessClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new NtsBusinessClient(builder.build(), "CHANGE_ME_TEST+key/=");
    }

    @Test
    @DisplayName("공식 진위확인 요청 형식과 인증키 인코딩을 사용하고 영업 중인 사업자를 허용한다")
    void validate_validBusiness_succeeds() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST)).andExpect(content().json("""
                {"businesses":[{"b_no":"1234567891","p_nm":"테스트 대표","start_dt":"20200101"}]}
                """)).andRespond(withSuccess(response("01", "01", NUMBER), MediaType.APPLICATION_JSON));
        client.validate(NUMBER, " 테스트 대표 ", OPENED_ON);
        server.verify();
    }

    @Test
    @DisplayName("HTTP 200이어도 진위 불일치는 가입 불가로 처리한다")
    void validate_mismatchedInformation_throwsException() {
        server.expect(requestTo(URL)).andRespond(withSuccess(response("02", "", NUMBER), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.validate(NUMBER, "테스트 대표", OPENED_ON))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.AUTH_BUSINESS_INVALID));
    }

    @Test
    @DisplayName("휴업·폐업 사업자는 거절한다")
    void validate_suspendedOrClosed_throwsException() {
        for (String status : new String[]{"02", "03"}) {
            server.reset();
            server.expect(requestTo(URL)).andRespond(withSuccess(response("01", status, NUMBER), MediaType.APPLICATION_JSON));
            assertThatThrownBy(() -> client.validate(NUMBER, "테스트 대표", OPENED_ON))
                    .isInstanceOfSatisfying(BusinessException.class,
                            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.AUTH_BUSINESS_INVALID));
        }
    }

    @Test
    @DisplayName("비정상 본문·번호 불일치·누락 응답을 성공으로 처리하지 않는다")
    void validate_invalidResponse_throwsLookupError() {
        for (String response : new String[]{"{}", "{\"status_code\":\"OK\",\"data\":[]}",
                response("01", "01", "0000000000"), response("01", "", NUMBER),
                "{\"status_code\":\"SERVICE_KEY_IS_NOT_REGISTERED_ERROR\"}"}) {
            server.reset();
            server.expect(requestTo(URL)).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
            assertLookupFailure();
        }
    }

    @Test
    @DisplayName("국세청 장애와 타임아웃을 구분된 오류로 반환하고 키가 담긴 원본 예외는 노출하지 않는다")
    void validate_providerFailure_doesNotLeakKey() {
        server.expect(requestTo(URL)).andRespond(withServerError());
        assertLookupFailure();
        server.reset();
        server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("dummy")));
        assertLookupFailure();
    }

    @Test
    @DisplayName("인증키 미설정이면 외부 호출 없이 실패한다")
    void validate_missingKey_throwsLookupError() {
        client = new NtsBusinessClient(RestClient.create(), "CHANGE_ME");
        assertLookupFailure();
        server.verify();
    }

    private void assertLookupFailure() {
        assertThatThrownBy(() -> client.validate(NUMBER, "테스트 대표", OPENED_ON))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.AUTH_BUSINESS_LOOKUP_FAILED);
                    assertThat(error.getCause()).isNull();
                    assertThat(error.getMessage()).doesNotContain("CHANGE_ME_TEST", "serviceKey");
                });
    }

    private static String response(String valid, String status, String number) {
        return """
                {"status_code":"OK","data":[{"b_no":"%s","valid":"%s",
                 "status":{"b_no":"%s","b_stt_cd":"%s"}}]}
                """.formatted(number, valid, number, status);
    }
}
