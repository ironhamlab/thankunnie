package com.ucamp.app.domain.payment.client;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import lombok.extern.slf4j.Slf4j;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 포트원 V1 REST API 호출. 토큰 발급 후 결제 단건을 조회한다.
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



            this.restClient = builder.baseUrl(baseUrl)
                    .build();
            this.impKey = impKey;
            this.impSecret = impSecret;
            this.includeSandbox = includeSandbox;
        }

        public PortOnePayment getPayment(String impUid) {

            PortOneResponse<PortOnePayment> body = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/payments/{impUid}")
                            .queryParam("include_sandbox", includeSandbox)
                            .build(impUid))
                    .header(HttpHeaders.AUTHORIZATION, getAccessToken())
                    .retrieve()
                    // 먼저 등록한 핸들러가 우선 적용되므로 404 를 일반 오류보다 앞에 둔다
                    .onStatus(status -> status.isSameCodeAs(HttpStatus.NOT_FOUND), (request, response) -> {
                        String errorBody = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
                    })
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new BusinessException(ErrorCode.PAYMENT_PORTONE_ERROR);
                    })
                    .body(new ParameterizedTypeReference<PortOneResponse<PortOnePayment>>() {
                    });
            return unwrap(body);
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
