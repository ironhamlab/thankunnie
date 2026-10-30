package com.ucamp.app.domain.user.dto.response;

import com.ucamp.app.domain.user.entity.Terms;
import com.ucamp.app.domain.user.entity.TermsCode;

/**
 * 약관 정보. 코드별 최신 버전만 내려준다.
 *
 * @param code     약관 코드
 * @param version  버전
 * @param title    제목
 * @param required 회원가입 필수 동의 여부
 */
public record TermsResponse(TermsCode code, int version, String title, boolean required) {

    public static TermsResponse from(Terms terms) {
        return new TermsResponse(terms.getCode(), terms.getVersion(), terms.getTitle(), terms.isRequired());
    }
}
