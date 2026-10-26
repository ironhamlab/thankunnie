package com.ucamp.app.domain.payment.client;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 포트원 V1 REST API 호출. 토큰 발급 후 결제 단건 조회와 결제 취소를 한다.
 */
@Slf4j
@Component
public class PortOneClient {

    private final boolean includeSandbox;
    private final RestClient restClient;
    private final String impKey;
    private final String impSecret;

    public PortOneClient(RestClient.Builder builder,
                         @Value("${iamport.base-url}") String baseUrl,
                         @Value("${iamport.imp-key}") String impKey,
                         @Value("${iamport.imp-secret}") String impSecret,
                         @Value("${iamport.include-sandbox}") boolean includeSandbox) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        this.restClient = builder.baseUrl(baseUrl)
                // 타임아웃 설정이 실제로 적용되도록 요청 팩토리를 연결한다
                .requestFactory(requestFactory)
                .build();
        this.impKey = impKey;
        this.impSecret = impSecret;
        this.includeSandbox = includeSandbox;
    }

    public PortOnePayment getPayment(String impUid) {
        try {
            PortOneResponse<PortOnePayment> body = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/payments/{impUid}")
                            .queryParam("include_sandbox", includeSandbox)
                            .build(impUid))
                    .header(HttpHeaders.AUTHORIZATION, getAccessToken())
                    .retrieve()
                    // 먼저 등록한 핸들러가 우선 적용되므로 404 를 일반 오류보다 앞에 둔다
                    .onStatus(status -> status.isSameCodeAs(HttpStatus.NOT_FOUND), (request, response) -> {
                        String errorBody = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        log.warn("포트원 결제 조회 404: impUid={}, body={}", impUid, errorBody);
                        throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
                    })
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new BusinessException(ErrorCode.PAYMENT_PORTONE_ERROR);
                    })
                    .body(new ParameterizedTypeReference<PortOneResponse<PortOnePayment>>() {
                    });
            return unwrap(body);
        } catch (ResourceAccessException e) {
            // 연결 실패·타임아웃 등 포트원에 닿지 못한 경우
            log.warn("포트원 연결 실패: impUid={}, cause={}", impUid, e.getMessage());
            throw new BusinessException(ErrorCode.PAYMENT_PORTONE_ERROR);

        }
    }

    /**
     * 결제를 취소(환불)한다. 이미 취소된 결제 등은 HTTP 200 + code != 0 으로 오므로 unwrap 에서 예외가 난다.
     *
     * @param impUid 포트원 결제 고유번호
     * @param amount 취소 금액
     * @param reason 취소 사유
     */
    public PortOnePayment cancelPayment(String impUid, int amount, String reason) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("imp_uid", impUid);
        requestBody.put("amount", amount);
        requestBody.put("reason", reason);

        try {
            PortOneResponse<PortOnePayment> body = restClient.post()
                    .uri("/payments/cancel")
                    .header(HttpHeaders.AUTHORIZATION, getAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        String errorBody = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        log.warn("포트원 결제 취소 실패: impUid={}, status={}, body={}",
                                impUid, response.getStatusCode(), errorBody);
                        throw new BusinessException(ErrorCode.PAYMENT_PORTONE_ERROR);
                    })
                    .body(new ParameterizedTypeReference<PortOneResponse<PortOnePayment>>() {
                    });
            if (body != null && body.code() != 0) {
                log.warn("포트원 결제 취소 거절: impUid={}, code={}", impUid, body.code());
            }
            return unwrap(body);
        } catch (ResourceAccessException e) {
            // 타임아웃이면 포트원에서는 취소가 됐을 수도 있다. 같은 imp_uid 를 조회해 상태를 확인해야 한다
            log.warn("포트원 연결 실패(취소): impUid={}, cause={}", impUid, e.getMessage());
            throw new BusinessException(ErrorCode.PAYMENT_PORTONE_ERROR);
        }
    }

    private String getAccessToken() {
        PortOneResponse<PortOneToken> body = restClient.post()
                .uri("/users/getToken")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("imp_key", impKey, "imp_secret", impSecret))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new BusinessException(ErrorCode.PAYMENT_PORTONE_ERROR);
                })
                .body(new ParameterizedTypeReference<PortOneResponse<PortOneToken>>() {
                });
        String accessToken = unwrap(body).accessToken();

        return accessToken;
    }

    private <T> T unwrap(PortOneResponse<T> body) {
        if (body == null || body.code() != 0 || body.response() == null) {
            throw new BusinessException(ErrorCode.PAYMENT_PORTONE_ERROR);
        }
        return body.response();
    }
}
