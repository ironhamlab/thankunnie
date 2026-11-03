package com.ucamp.app.domain.auth.client;

import java.time.LocalDate;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;

/**
 * 실제 사업자번호 없이 매장 가입 화면을 확인하기 위한 로컬 전용 가짜 국세청 조회.
 * local 프로필에서 nts.fake=true 일 때만 실제 조회 대신 쓰며, 국세청을 호출하지 않는다.
 * 대표자명이 "테스트 대표"면 통과하고, 휴업·폐업 번호와 다른 대표자명은 실제 조회와 같은 오류를 낸다.
 */
@Component
@Primary
@Profile("local")
@ConditionalOnProperty(name = "nts.fake", havingValue = "true")
public class LocalFakeNtsBusinessClient extends NtsBusinessClient {

    static final String REPRESENTATIVE_NAME = "테스트 대표";
    static final Set<String> SUSPENDED_NUMBERS = Set.of("2222222227");
    static final Set<String> CLOSED_NUMBERS = Set.of("3333333336");
    private static final Logger log = LoggerFactory.getLogger(LocalFakeNtsBusinessClient.class);

    public LocalFakeNtsBusinessClient() {
        super(null, null);
        log.warn("국세청 사업자 조회를 로컬 가짜 모드로 실행합니다. 실제 국세청을 조회하지 않습니다.");
    }

    @Override
    public void validate(String businessNumber, String representativeName, LocalDate openedOn) {
        if (!REPRESENTATIVE_NAME.equals(representativeName.trim())) {
            throw new BusinessException(ErrorCode.AUTH_BUSINESS_INVALID,
                    "사업자등록번호·대표자명·개업일자가 국세청 정보와 일치하지 않습니다.");
        }
        if (SUSPENDED_NUMBERS.contains(businessNumber) || CLOSED_NUMBERS.contains(businessNumber)) {
            throw new BusinessException(ErrorCode.AUTH_BUSINESS_INVALID,
                    "휴업·폐업 사업자는 매장을 등록할 수 없습니다.");
        }
    }
}
