package com.ucamp.app.domain.user.dto.response;

import java.time.OffsetDateTime;

import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.user.entity.Terms;
import com.ucamp.app.domain.user.entity.TermsCode;
import com.ucamp.app.domain.user.entity.UserTermsAgreement;

/**
 * 내가 동의한 약관 (최신 버전 기준).
 *
 * @param code     약관 코드
 * @param version  동의한 약관 버전
 * @param title    제목
 * @param required 회원가입 필수 동의 여부
 * @param agreedAt 동의 시각 (+09:00)
 */
public record UserTermsAgreementResponse(
        TermsCode code,
        int version,
        String title,
        boolean required,
        OffsetDateTime agreedAt) {

    public static UserTermsAgreementResponse of(Terms terms, UserTermsAgreement agreement) {
        return new UserTermsAgreementResponse(terms.getCode(), terms.getVersion(), terms.getTitle(),
                terms.isRequired(), TimeUtils.toOffset(agreement.getAgreedAt()));
    }
}
