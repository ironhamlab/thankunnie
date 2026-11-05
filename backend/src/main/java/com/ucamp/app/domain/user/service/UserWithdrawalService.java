package com.ucamp.app.domain.user.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.lock.UserLock;
import com.ucamp.app.domain.notification.repository.NotificationDispatchRepository;
import com.ucamp.app.domain.notification.repository.NotificationInboxRepository;
import com.ucamp.app.domain.notification.repository.NotificationSubscriptionRepository;
import com.ucamp.app.domain.reservation.repository.ReservationRepository;
import com.ucamp.app.domain.user.dto.request.UserWithdrawRequest;
import com.ucamp.app.domain.user.entity.AppUser;
import com.ucamp.app.domain.user.repository.AppUserRepository;

/**
 * 고객 회원 탈퇴. 예약·결제 기록은 매장 장부와 정산에 필요해 지우지 않고, 회원 상태만 INACTIVE 로 바꾼다.
 *
 * <p>탈퇴하면 알림을 해제하고, 받은 알림을 지우고, 아직 보내지 않은 알림 문자를 취소한다.
 * 예약 대기열(번호표)은 서버 메모리에 있고 대기 화면이 순서를 묻지 않으면 곧 빠지므로 따로 지우지 않는다.
 * 이미 발급한 토큰은 다음 요청부터 CustomerLoginStatusChecker 가 비로그인으로 처리한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserWithdrawalService {

    // BCrypt 가 처리할 수 있는 비밀번호 최대 길이 (UTF-8). 넘으면 비교하지 않고 틀린 것으로 본다
    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    private final AppUserRepository appUserRepository;
    private final ReservationRepository reservationRepository;
    private final NotificationSubscriptionRepository subscriptionRepository;
    private final NotificationInboxRepository inboxRepository;
    private final NotificationDispatchRepository dispatchRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserLock userLock;
    private final Clock clock;

    /**
     * 로그인한 고객을 탈퇴시킨다.
     *
     * @param loginUser 로그인한 고객. 관리자·매장은 403
     * @param request   본인 확인용 비밀번호
     * @throws BusinessException 비밀번호가 틀리면 USER_WITHDRAW_PASSWORD_MISMATCH,
     *                           결제 대기·방문 전 예약이나 환불 처리 중인 취소가 있으면 USER_WITHDRAW_HAS_RESERVATION
     */
    @Transactional
    public void withdraw(AuthUser loginUser, UserWithdrawRequest request) {
        loginUser.requireRole(UserRole.CUSTOMER);
        // 예약 점유와 같은 회원 잠금으로 줄 세워, 검사를 통과한 뒤 새 예약이 생기지 않게 한다
        userLock.lock(loginUser.userId());
        AppUser user = appUserRepository.findById(loginUser.userId())
                .filter(AppUser::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!matchesPassword(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.USER_WITHDRAW_PASSWORD_MISMATCH);
        }
        // 잠금 대기 중 예약이 끝났을 수 있으므로 잠금 뒤 시각으로 판단한다
        LocalDateTime now = LocalDateTime.now(clock);
        // 결제 확정은 회원 잠금을 쓰지 않는다. 결제 대기도 막아, 확정 중이든 확정 뒤든 여기서 걸리게 한다
        if (reservationRepository.existsInProgressByUser(user.getUserId(), now)) {
            throw new BusinessException(ErrorCode.USER_WITHDRAW_HAS_RESERVATION);
        }

        user.withdraw();
        appUserRepository.flush();
        subscriptionRepository.deactivateAllByUserId(user.getUserId(), now);
        inboxRepository.deleteAllByUserId(user.getUserId());
        dispatchRepository.skipPendingByUserId(user.getUserId(), now);
    }

    private boolean matchesPassword(String rawPassword, String passwordHash) {
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, passwordHash);
    }
}
