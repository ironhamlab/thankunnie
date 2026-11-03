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
import com.ucamp.app.domain.user.entity.ShopTermsAgreement;
import com.ucamp.app.domain.user.entity.Terms;
import com.ucamp.app.domain.user.entity.TermsCode;
import com.ucamp.app.domain.user.entity.TermsTarget;
import com.ucamp.app.domain.user.entity.UserTermsAgreement;
import com.ucamp.app.domain.user.repository.ShopTermsAgreementRepository;
import com.ucamp.app.domain.user.repository.TermsRepository;
import com.ucamp.app.domain.user.repository.UserTermsAgreementRepository;

/**
 * 약관 조회와 회원·매장 약관 동의.
 *
 * <p>동의는 항상 코드별 <b>최신 버전</b> 약관을 기준으로 한다. 약관을 개정해 새 버전 행을 넣으면
 * 이전 버전에만 동의한 회원은 미동의로 판단되어 다시 동의를 받게 된다.
 * 고객 약관과 매장 약관은 코드의 대상({@link TermsTarget})으로 나누며, 서로의 가입·동의에 섞이지 않는다.
 * 다른 도메인은 {@link #requireAgreed(Long, TermsCode)} 로 선택 동의(문자 수신·초보 시술 촬영) 여부를 검사한다.
 * 선택 약관은 철회할 수 있고, 알림 문자 발송은 {@link #hasAgreed(Long, TermsCode)} 로 동의한 회원에게만 보낸다.
 */
@Service
@Transactional(readOnly = true)
public class TermsAgreementService {

    private final TermsRepository termsRepository;
    private final UserTermsAgreementRepository agreementRepository;
    private final ShopTermsAgreementRepository shopAgreementRepository;
    private final UserLock userLock;
    private final Clock clock;

    public TermsAgreementService(TermsRepository termsRepository,
            UserTermsAgreementRepository agreementRepository,
            ShopTermsAgreementRepository shopAgreementRepository, UserLock userLock, Clock clock) {
        this.termsRepository = termsRepository;
        this.agreementRepository = agreementRepository;
        this.shopAgreementRepository = shopAgreementRepository;
        this.userLock = userLock;
        this.clock = clock;
    }

    /** 대상별 코드별 최신 버전 약관 목록. 고객·매장 가입 화면의 체크박스 목록이다. */
    public List<TermsResponse> getTerms(TermsTarget target) {
        return latestTerms(target).values().stream()
                .map(TermsResponse::from)
                .toList();
    }

    /**
     * 고객 회원가입 요청의 동의 목록을 검사한다. 회원을 저장하기 전에 호출한다.
     * 최신 필수 약관이 하나라도 빠지면 400, 없는 약관 코드(매장 약관 포함)가 있으면 404.
     * 필수 약관이 하나도 등록되어 있지 않으면(운영 DB 초기 데이터 누락) 동의 없이 가입되지 않도록 500 으로 막는다.
     */
    public void validateSignupAgreements(Collection<TermsCode> agreedCodes) {
        validateAgreements(TermsTarget.CUSTOMER, agreedCodes);
    }

    /** 매장 가입 요청의 동의 목록을 검사한다. 규칙은 고객 가입과 같고 매장 약관만 받는다. */
    public void validateShopSignupAgreements(Collection<TermsCode> agreedCodes) {
        validateAgreements(TermsTarget.SHOP, agreedCodes);
    }

    private void validateAgreements(TermsTarget target, Collection<TermsCode> agreedCodes) {
        Map<TermsCode, Terms> latest = latestTerms(target);
        if (latest.values().stream().noneMatch(Terms::isRequired)) {
            throw new IllegalStateException("필수 약관(TERMS)이 등록되어 있지 않아 가입을 처리할 수 없습니다. 대상: " + target);
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
        Map<TermsCode, Terms> latest = latestTerms(TermsTarget.CUSTOMER);
        LocalDateTime now = LocalDateTime.now(clock);
        List<UserTermsAgreement> agreements = toSet(agreedCodes).stream()
                .map(code -> newAgreement(userId, latest.get(code), now))
                .toList();
        agreementRepository.saveAll(agreements);
    }

    /** 가입한 매장의 동의를 저장한다. 매장 저장과 같은 트랜잭션에서 호출한다. */
    @Transactional
    public void agreeOnShopSignup(Long shopId, Collection<TermsCode> agreedCodes) {
        validateShopSignupAgreements(agreedCodes);
        Map<TermsCode, Terms> latest = latestTerms(TermsTarget.SHOP);
        LocalDateTime now = LocalDateTime.now(clock);
        List<ShopTermsAgreement> agreements = toSet(agreedCodes).stream()
                .map(code -> ShopTermsAgreement.builder()
                        .shopId(shopId)
                        .termsId(latest.get(code).getTermsId())
                        .agreedAt(now)
                        .build())
                .toList();
        shopAgreementRepository.saveAll(agreements);
    }

    /** 내가 동의한 최신 버전 약관 목록. 고객만 조회할 수 있다. */
    public List<UserTermsAgreementResponse> getMyAgreements(AuthUser loginUser) {
        loginUser.requireRole(UserRole.CUSTOMER);
        Map<Long, Terms> latestById = latestTerms(TermsTarget.CUSTOMER).values().stream()
                .collect(Collectors.toMap(Terms::getTermsId, Function.identity()));
        return agreementRepository.findByUserIdAndTermsIdIn(loginUser.userId(), latestById.keySet()).stream()
                .filter(UserTermsAgreement::isActive)
                .map(agreement -> UserTermsAgreementResponse.of(latestById.get(agreement.getTermsId()), agreement))
                .sorted(Comparator.comparing(UserTermsAgreementResponse::code))
                .toList();
    }

    /**
     * 최신 버전 약관에 동의한다. 이미 동의했으면 기존 동의를 그대로 돌려준다 (반복 요청 안전).
     * 철회했던 동의면 같은 행을 되살린다.
     * 같은 고객의 동시 요청이 둘 다 '없음'으로 보고 INSERT 해 UK 위반(500)이 나지 않도록 회원 잠금 안에서 조회·저장한다.
     * 고객만 동의할 수 있다.
     */
    @Transactional
    public UserTermsAgreementResponse agree(AuthUser loginUser, TermsCode code) {
        loginUser.requireRole(UserRole.CUSTOMER);
        Terms terms = latestTerms(TermsTarget.CUSTOMER).get(code);
        if (terms == null) {
            throw new BusinessException(ErrorCode.TERMS_NOT_FOUND);
        }
        userLock.lock(loginUser.userId());
        UserTermsAgreement agreement = agreementRepository
                .findByUserIdAndTermsIdIn(loginUser.userId(), List.of(terms.getTermsId())).stream()
                .findFirst()
                .orElseGet(() -> agreementRepository.save(
                        newAgreement(loginUser.userId(), terms, LocalDateTime.now(clock))));
        agreement.reagree(LocalDateTime.now(clock));
        return UserTermsAgreementResponse.of(terms, agreement);
    }

    /**
     * 선택 약관 동의를 철회한다. 이전 버전에 남은 동의까지 모두 철회한다.
     * 동의한 적이 없거나 이미 철회했으면 아무것도 하지 않는다 (반복 요청 안전).
     * 필수 약관은 철회할 수 없다(400). 고객만 철회할 수 있다.
     */
    @Transactional
    public void withdraw(AuthUser loginUser, TermsCode code) {
        loginUser.requireRole(UserRole.CUSTOMER);
        Terms latest = latestTerms(TermsTarget.CUSTOMER).get(code);
        if (latest == null) {
            throw new BusinessException(ErrorCode.TERMS_NOT_FOUND);
        }
        if (latest.isRequired()) {
            throw new BusinessException(ErrorCode.TERMS_REQUIRED_NOT_AGREED);
        }
        userLock.lock(loginUser.userId());
        LocalDateTime now = LocalDateTime.now(clock);
        agreementsOf(loginUser.userId(), code).forEach(agreement -> agreement.withdraw(now));
    }

    /** 회원이 해당 약관의 최신 버전에 동의했는지. 약관이 등록되어 있지 않으면 false. */
    public boolean hasAgreed(Long userId, TermsCode code) {
        Terms terms = latestTerms(TermsTarget.CUSTOMER).get(code);
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

    // 코드 순서(enum 선언 순)로 정렬된, 대상별 코드별 최신 버전 약관
    private Map<TermsCode, Terms> latestTerms(TermsTarget target) {
        Map<TermsCode, Terms> latest = new EnumMap<>(TermsCode.class);
        for (Terms terms : termsRepository.findAllByOrderByCodeAscVersionAsc()) {
            if (terms.getCode().getTarget() == target) {
                latest.put(terms.getCode(), terms);   // 버전 오름차순이라 마지막에 넣은 것이 최신
            }
        }
        return latest;
    }

    // 회원이 해당 약관의 모든 버전에 남긴 동의
    private List<UserTermsAgreement> agreementsOf(Long userId, TermsCode code) {
        List<Long> termsIds = termsRepository.findAllByOrderByCodeAscVersionAsc().stream()
                .filter(terms -> terms.getCode() == code)
                .map(Terms::getTermsId)
                .toList();
        if (termsIds.isEmpty()) {
            return List.of();
        }
        return agreementRepository.findByUserIdAndTermsIdIn(userId, termsIds);
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
