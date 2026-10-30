package com.ucamp.app.domain.auth.service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.JwtProvider;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.auth.dto.request.AuthCustomerSignupRequest;
import com.ucamp.app.domain.auth.dto.request.AuthLoginRequest;
import com.ucamp.app.domain.auth.dto.request.AuthShopSignupRequest;
import com.ucamp.app.domain.auth.dto.response.AuthShopCategoryResponse;
import com.ucamp.app.domain.auth.dto.response.AuthTokenResponse;
import com.ucamp.app.domain.auth.entity.ShopAccount;
import com.ucamp.app.domain.auth.repository.ShopAccountRepository;
import com.ucamp.app.domain.shop.repository.ShopCategoryRepository;
import com.ucamp.app.domain.user.entity.AppUser;
import com.ucamp.app.domain.user.entity.AppUserRole;
import com.ucamp.app.domain.user.repository.AppUserRepository;
import com.ucamp.app.domain.user.service.TermsAgreementService;

/**
 * 고객·사장님 회원가입과 로그인. 성공하면 액세스 토큰을 발급한다.
 *
 * <p>고객·관리자는 APP_USER, 사장님은 SHOP 에 계정이 있어 아이디 중복과 로그인을 각 테이블 기준으로 처리한다.
 * 가입은 아이디 사전 확인 후 저장하고, 동시에 같은 아이디로 가입하면 UNIQUE 제약 위반을 중복 아이디로 바꾼다.
 */
@Service
@Transactional(readOnly = true)
public class AuthService {

    // BCrypt 가 처리할 수 있는 비밀번호 최대 길이 (UTF-8). 가입에서도 같은 기준으로 막는다
    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;
    private static final String DUMMY_PASSWORD = "dummy-password-for-unknown-login-id";

    private final AppUserRepository appUserRepository;
    private final ShopAccountRepository shopAccountRepository;
    private final ShopCategoryRepository shopCategoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final TermsAgreementService termsAgreementService;
    // 없는 아이디로 로그인할 때 비교할 해시. 같은 인코더로 만들어 실제 계정과 비교 비용이 같다
    private final String dummyPasswordHash;

    public AuthService(AppUserRepository appUserRepository, ShopAccountRepository shopAccountRepository,
            ShopCategoryRepository shopCategoryRepository, PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider, TermsAgreementService termsAgreementService) {
        this.appUserRepository = appUserRepository;
        this.shopAccountRepository = shopAccountRepository;
        this.shopCategoryRepository = shopCategoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.termsAgreementService = termsAgreementService;
        this.dummyPasswordHash = passwordEncoder.encode(DUMMY_PASSWORD);
    }

    /** 고객으로 가입하고 바로 로그인한다. 약관 동의도 같은 트랜잭션에서 저장한다. */
    @Transactional
    public AuthTokenResponse signupCustomer(AuthCustomerSignupRequest request) {
        if (appUserRepository.existsByLoginId(request.loginId())) {
            throw new BusinessException(ErrorCode.AUTH_DUPLICATE_LOGIN_ID);
        }
        termsAgreementService.validateSignupAgreements(request.agreedTerms());
        AppUser user = AppUser.builder()
                .loginId(request.loginId())
                .passwordHash(passwordEncoder.encode(request.password()))
                .name(request.name().trim())
                .phone(normalizeMobilePhone(request.phone()))
                .role(AppUserRole.USER)
                .build();
        AppUser saved = saveUniqueLoginId(() -> appUserRepository.saveAndFlush(user));
        termsAgreementService.agreeOnSignup(saved.getUserId(), request.agreedTerms());
        return issue(saved.toAuthUser());
    }

    /** 매장 정보와 함께 사장님 계정으로 가입하고 바로 로그인한다. */
    @Transactional
    public AuthTokenResponse signupShop(AuthShopSignupRequest request) {
        if (shopAccountRepository.existsByLoginId(request.loginId())) {
            throw new BusinessException(ErrorCode.AUTH_DUPLICATE_LOGIN_ID);
        }
        if (!shopCategoryRepository.existsById(request.categoryId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "존재하지 않는 매장 카테고리입니다.");
        }
        ShopAccount account = ShopAccount.builder()
                .loginId(request.loginId())
                .passwordHash(passwordEncoder.encode(request.password()))
                .categoryId(request.categoryId())
                .name(request.name().trim())
                .regionCode(request.regionCode())
                .address(request.address())
                .addressDetail(request.addressDetail())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .phone(request.phone())
                .description(request.description())
                .notice(request.notice())
                .build();
        ShopAccount saved = saveUniqueLoginId(() -> shopAccountRepository.saveAndFlush(account));
        return issue(saved.toAuthUser());
    }

    /** 고객·관리자 로그인. 아이디가 없을 때와 비밀번호가 틀릴 때를 구분하지 않는다. */
    public AuthTokenResponse loginCustomer(AuthLoginRequest request) {
        AppUser user = appUserRepository.findByLoginId(request.loginId()).orElse(null);
        if (!matchesPassword(request.password(), user == null ? null : user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_LOGIN_FAILED);
        }
        // 비밀번호가 맞은 뒤에만 비활성 여부를 알려 계정 존재 여부가 드러나지 않게 한다
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.AUTH_INACTIVE_ACCOUNT);
        }
        return issue(user.toAuthUser());
    }

    /**
     * 사장님(매장 계정) 로그인. SHOP.STATUS 는 매장 노출·판매 여부라 계정 잠금으로 쓰지 않는다.
     * INACTIVE 매장도 로그인해서 매장을 관리할 수 있어야 한다.
     */
    public AuthTokenResponse loginShop(AuthLoginRequest request) {
        ShopAccount account = shopAccountRepository.findByLoginId(request.loginId()).orElse(null);
        if (!matchesPassword(request.password(), account == null ? null : account.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_LOGIN_FAILED);
        }
        return issue(account.toAuthUser());
    }

    /** 매장 가입 화면의 카테고리 선택지. */
    public List<AuthShopCategoryResponse> getShopCategories() {
        return shopCategoryRepository.findAllByOrderByNameAsc().stream()
                .map(AuthShopCategoryResponse::from)
                .toList();
    }

    // 계정이 없어도 같은 비용의 BCrypt 비교를 한 번 수행해 응답 시간으로 아이디 존재 여부가 드러나지 않게 한다.
    // 72바이트를 넘는 입력은 BCrypt 가 예외(500)를 던지므로 비교하지 않고 실패로 본다. 가입에서 막아 그런 계정은 없다
    private boolean matchesPassword(String rawPassword, String passwordHash) {
        boolean comparable = passwordHash != null
                && rawPassword.getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_PASSWORD_BYTES;
        if (!comparable) {
            passwordEncoder.matches(DUMMY_PASSWORD, dummyPasswordHash);
            return false;
        }
        return passwordEncoder.matches(rawPassword, passwordHash);
    }

    private AuthTokenResponse issue(AuthUser authUser) {
        return AuthTokenResponse.of(jwtProvider.issue(authUser), authUser);
    }

    // 사전 확인과 저장 사이에 같은 아이디가 먼저 가입되면 LOGIN_ID UNIQUE 위반이 난다.
    // 필수값·길이·카테고리는 앞에서 검증했으므로 이 시점의 제약 위반은 아이디 중복으로 본다
    private static <T> T saveUniqueLoginId(Supplier<T> save) {
        try {
            return save.get();
        } catch (DataIntegrityViolationException e) {
            ErrorCode errorCode = ErrorCode.AUTH_DUPLICATE_LOGIN_ID;
            throw new BusinessException(errorCode, errorCode.getMessage(), e);
        }
    }

    // 요청 검증을 통과한 휴대폰 번호를 문자 발송·초기 데이터와 같은 010-0000-0000 형식으로 맞춘다
    private static String normalizeMobilePhone(String phone) {
        String digits = phone.replaceAll("\\D", "");
        int middleEnd = digits.length() - 4;
        return digits.substring(0, 3) + "-" + digits.substring(3, middleEnd) + "-" + digits.substring(middleEnd);
    }
}
