package com.ucamp.app.domain.auth.client;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;

/** 국세청 진위확인 응답에 포함되는 상태로 계속사업 여부까지 확인한다. */
@Component
public class NtsBusinessClient {

    private static final String URL = "https://api.odcloud.kr/api/nts-businessman/v1/validate";
    private final RestClient restClient;
    private final String serviceKey;

    @Autowired
    public NtsBusinessClient(@Value("${nts.service-key:${NTS_SERVICE_KEY:}}") String serviceKey) {
        this(createClient(), serviceKey);
    }

    NtsBusinessClient(RestClient restClient, String serviceKey) {
        this.restClient = restClient;
        this.serviceKey = serviceKey;
    }

    public void validate(String businessNumber, String representativeName, LocalDate openedOn) {
        if (serviceKey == null || serviceKey.isBlank() || serviceKey.equals("CHANGE_ME")) {
            throw lookupFailed();
        }
        // Decoding 키를 URI 변수로 인코딩한다. 키가 포함된 URL·원본 예외는 로그나 응답으로 남기지 않는다.
        URI uri = UriComponentsBuilder.fromUriString(URL).queryParam("serviceKey", "{key}")
                .queryParam("returnType", "JSON").encode().buildAndExpand(serviceKey).toUri();
        Map<String, Object> body = Map.of("businesses", List.of(Map.of(
                "b_no", businessNumber,
                "p_nm", representativeName.trim(),
                "start_dt", openedOn.format(DateTimeFormatter.BASIC_ISO_DATE))));
        JsonNode result;
        try {
            result = restClient.post().uri(uri).contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            throw lookupFailed();
        }
        if (result == null || !"OK".equals(result.path("status_code").asText())
                || !result.path("data").isArray() || result.path("data").size() != 1) {
            throw lookupFailed();
        }
        JsonNode item = result.path("data").get(0);
        if (!businessNumber.equals(item.path("b_no").asText())) {
            throw lookupFailed();
        }
        String valid = item.path("valid").asText();
        if ("02".equals(valid)) {
            throw new BusinessException(ErrorCode.AUTH_BUSINESS_INVALID,
                    "사업자등록번호·대표자명·개업일자가 국세청 정보와 일치하지 않습니다.");
        }
        if (!"01".equals(valid) || !businessNumber.equals(item.path("status").path("b_no").asText())) {
            throw lookupFailed();
        }
        String status = item.path("status").path("b_stt_cd").asText();
        if ("02".equals(status) || "03".equals(status)) {
            throw new BusinessException(ErrorCode.AUTH_BUSINESS_INVALID,
                    "휴업·폐업 사업자는 매장을 등록할 수 없습니다.");
        }
        if (!"01".equals(status)) {
            throw lookupFailed();
        }
    }

    private static RestClient createClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(4));
        return RestClient.builder().requestFactory(factory).build();
    }

    private static BusinessException lookupFailed() {
        return new BusinessException(ErrorCode.AUTH_BUSINESS_LOOKUP_FAILED);
    }
}
