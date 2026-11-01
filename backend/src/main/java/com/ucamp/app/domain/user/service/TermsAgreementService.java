package com.ucamp.app.domain.user.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.lock.UserLock;
import com.ucamp.app.domain.user.dto.response.TermsResponse;
import com.ucamp.app.domain.user.dto.response.UserTermsAgreementResponse;
import com.ucamp.app.domain.user.entity.Terms;
import com.ucamp.app.domain.user.entity.TermsCode;
import com.ucamp.app.domain.user.entity.UserTermsAgreement;
import com.ucamp.app.domain.user.repository.TermsRepository;
import com.ucamp.app.domain.user.repository.UserTermsAgreementRepository;

/**
 * 약관 조회와 회원 약관 동의.
 *
 * <p>동의는 항상 코드별 <b>최신 버전</b> 약관을 기준으로 한다. 약관을 개정해 새 버전 행을 넣으면
 * 이전 버전에만 동의한 회원은 미동의로 판단되어 다시 동의를 받게 된다.
 * 다른 도메인은 {@link #requireAgreed(Long, TermsCode)} 로 선택 동의(문자 수신·초보 시술 촬영) 여부를 검사한다.
 */
@Service
@Transactional(readOnly = true)
public class TermsAgreementService {

    private final TermsRepository termsRepository;
    private final UserTermsAgreementRepository agreementRepository;
    private final UserLock userLock;
    private final Clock clock;

    public TermsAgreementService(TermsRepository termsRepository,
            UserTermsAgreementRepository agreementRepository, UserLock userLock, Clock clock) {
        this.termsRepository = termsRepository;
        this.agreementRepository = agreementRepository;
        this.userLock = userLock;
        this.clock = clock;
    }

    /** 코드별 최신 버전 약관 목록. 회원가입 화면의 체크박스 목록이다. */
    public List<TermsResponse> getTerms() {
        return latestTerms().values().stream()
                .map(TermsResponse::from)
                .toList();
    }

    /**
     * 회원가입 요청의 동의 목록을 검사한다. 회원을 저장하기 전에 호출한다.
     * 최신 필수 약관이 하나라도 빠지면 400, 없는 약관 코드가 있으면 404.
     * 필수 약관이 하나도 등록되어 있지 않으면(운영 DB 초기 데이터 누락) 동의 없이 가입되지 않도록 500 으로 막는다.
     */
    public void validateSignupAgreements(Collection<TermsCode> agreedCodes) {
        Map<TermsCode, Terms> latest = latestTerms();
        if (latest.values().stream().noneMatch(Terms::isRequired)) {
            throw new IllegalStateException("필수 약관(TERMS)이 등록되어 있지 않아 회원가입을 처리할 수 없습니다.");
        }
        Set<TermsCode> agreed = toSet(agreedCodes);
        if (!latest.keySet().containsAll(agreed)) {
            throw new BusinessException(ErrorCode.TERMS_NOT_FOUND);
        }
        boolean missingRequired = latest.values().stream()
                .anyMatch(terms -> terms.isRequired() && !agreed.contains(terms.getCode()));
        if (missingRequired) {
            throw new BusinessException(ErrorCode.TERMS_REQUIRED_NOT_AGREED);
        }
    }

    /** 가입한 회원의 동의를 저장한다. 회원 저장과 같은 트랜잭션에서 호출한다. */
    @Transactional
    public void agreeOnSignup(Long userId, Collection<TermsCode> agreedCodes) {
        validateSignupAgreements(agreedCodes);
        Map<TermsCode, Terms> latest = latestTerms();
        LocalDateTime now = LocalDateTime.now(clock);
        List<UserTermsAgreement> agreements = toSet(agreedCodes).stream()
                .map(code -> newAgreement(userId, latest.get(code), now))
                .toList();
        agreementRepository.saveAll(agreements);
    }

    /** 내가 동의한 최신 버전 약관 목록. 고객만 조회할 수 있다. */
    public List<UserTermsAgreementResponse> getMyAgreements(AuthUser loginUser) {
        loginUser.requireRole(UserRole.CUSTOMER);
        Map<Long, Terms> latestById = latestTerms().values().stream()
                .collect(Collectors.toMap(Terms::getTermsId, Function.identity()));
        return agreementRepository.findByUserIdAndTermsIdIn(loginUser.userId(), latestById.keySet()).stream()
                .filter(UserTermsAgreement::isActive)
                .map(agreement -> UserTermsAgreementResponse.of(latestById.get(agreement.getTermsId()), agreement))
                .sorted(Comparator.comparing(UserTermsAgreementResponse::code))
                .toList();
    }

    /**
     * 최신 버전 약관에 동의한다. 이미 동의했으면 기존 동의를 그대로 돌려준다 (반복 요청 안전).
     * 같은 고객의 동시 요청이 둘 다 '없음'으로 보고 INSERT 해 UK 위반(500)이 나지 않도록 회원 잠금 안에서 조회·저장한다.
     * 고객만 동의할 수 있다.
     */
    @Transactional
    public UserTermsAgreementResponse agree(AuthUser loginUser, TermsCode code) {
        loginUser.requireRole(UserRole.CUSTOMER);
        Terms terms = latestTerms().get(code);
        if (terms == null) {
            throw new BusinessException(ErrorCode.TERMS_NOT_FOUND);
        }
        userLock.lock(loginUser.userId());
        UserTermsAgreement agreement = agreementRepository
                .findByUserIdAndTermsIdIn(loginUser.userId(), List.of(terms.getTermsId())).stream()
                .findFirst()
                .orElseGet(() -> agreementRepository.save(
                        newAgreement(loginUser.userId(), terms, LocalDateTime.now(clock))));
        return UserTermsAgreementResponse.of(terms, agreement);
    }

    /** 회원이 해당 약관의 최신 버전에 동의했는지. 약관이 등록되어 있지 않으면 false. */
    public boolean hasAgreed(Long userId, TermsCode code) {
        Terms terms = latestTerms().get(code);
        return terms != null
                && agreementRepository.existsByUserIdAndTermsIdAndWithdrawnAtIsNull(userId, terms.getTermsId());
    }

    /**
     * 동의하지 않았으면 약관별 에러를 던진다. 프론트는 에러 코드를 보고 동의 모달을 띄운 뒤 다시 요청한다.
     * 문자 수신은 구독 등록, 초보 시술 촬영은 초보 시술 예약 생성에서 호출한다.
     */
    public void requireAgreed(Long userId, TermsCode code) {
        if (!hasAgreed(userId, code)) {
            throw new BusinessException(agreementRequiredError(code));
        }
    }

    private static ErrorCode agreementRequiredError(TermsCode code) {
        return switch (code) {
            case SMS_NOTIFICATION -> ErrorCode.TERMS_SMS_AGREEMENT_REQUIRED;
            case MODEL_PHOTO -> ErrorCode.TERMS_MODEL_PHOTO_AGREEMENT_REQUIRED;
            default -> ErrorCode.TERMS_REQUIRED_NOT_AGREED;
        };
    }

    // 코드 순서(enum 선언 순)로 정렬된, 코드별 최신 버전 약관
    private Map<TermsCode, Terms> latestTerms() {
        Map<TermsCode, Terms> latest = new EnumMap<>(TermsCode.class);
        for (Terms terms : termsRepository.findAllByOrderByCodeAscVersionAsc()) {
            latest.put(terms.getCode(), terms);   // 버전 오름차순이라 마지막에 넣은 것이 최신
        }
        return latest;
    }

    private static Set<TermsCode> toSet(Collection<TermsCode> codes) {
        Set<TermsCode> set = EnumSet.noneOf(TermsCode.class);
        set.addAll(codes);
        return set;
    }

    private static UserTermsAgreement newAgreement(Long userId, Terms terms, LocalDateTime now) {
        return UserTermsAgreement.builder()
                .userId(userId)
                .termsId(terms.getTermsId())
                .agreedAt(now)
                .build();
    }
}
