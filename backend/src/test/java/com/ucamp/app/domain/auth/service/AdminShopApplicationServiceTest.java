package com.ucamp.app.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.TestUser;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.auth.entity.ShopAccount;
import com.ucamp.app.domain.auth.entity.ShopAccountStatus;
import com.ucamp.app.domain.auth.repository.ShopAccountRepository;
import com.ucamp.app.domain.notification.event.ShopApplicationApprovedEvent;
import com.ucamp.app.domain.notification.event.ShopApplicationRejectedEvent;
import com.ucamp.app.domain.reservation.entity.Reservation;
import com.ucamp.app.domain.reservation.repository.ReservationRepository;
import com.ucamp.app.domain.shop.repository.ShopCategoryRepository;

@ExtendWith(MockitoExtension.class)
class AdminShopApplicationServiceTest {

    private static final Long SHOP_ID = 10L;
    private static final AuthUser ADMIN = TestUser.ADMIN.toAuthUser();

    @Mock
    private ShopAccountRepository shopAccountRepository;

    @Mock
    private ShopCategoryRepository shopCategoryRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AdminShopApplicationService adminShopApplicationService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        adminShopApplicationService = new AdminShopApplicationService(
                shopAccountRepository, shopCategoryRepository, reservationRepository, clock, eventPublisher);
    }

    @Test
    @DisplayName("승인하면 노출 상태로 바꾸고 승인 이벤트를 발행한다")
    void approve_publishesApprovedEvent() {
        ShopAccount shop = pendingShop();
        given(shopAccountRepository.findByIdForUpdate(SHOP_ID)).willReturn(Optional.of(shop));
        given(shopCategoryRepository.findById(1L)).willReturn(Optional.empty());

        adminShopApplicationService.approve(ADMIN, SHOP_ID);

        assertThat(shop.getStatus()).isEqualTo(ShopAccountStatus.ACTIVE);
        verify(eventPublisher).publishEvent(new ShopApplicationApprovedEvent(SHOP_ID));
    }

    @Test
    @DisplayName("거절하면 앞뒤 공백을 뺀 사유로 거절 이벤트를 발행한다")
    void reject_publishesRejectedEventWithTrimmedReason() {
        ShopAccount shop = pendingShop();
        given(shopAccountRepository.findByIdForUpdate(SHOP_ID)).willReturn(Optional.of(shop));
        given(shopCategoryRepository.findById(1L)).willReturn(Optional.empty());

        adminShopApplicationService.reject(ADMIN, SHOP_ID, "  서비스 대상 업종이 아니에요.  ");

        assertThat(shop.getStatus()).isEqualTo(ShopAccountStatus.REJECTED);
        assertThat(shop.getRejectReason()).isEqualTo("서비스 대상 업종이 아니에요.");
        verify(eventPublisher).publishEvent(
                new ShopApplicationRejectedEvent(SHOP_ID, "서비스 대상 업종이 아니에요."));
    }

    @Test
    @DisplayName("승인 대기가 아니면 승인 이벤트를 발행하지 않는다")
    void approve_notPending_doesNotPublish() {
        ShopAccount shop = pendingShop();
        shop.approve();
        given(shopAccountRepository.findByIdForUpdate(SHOP_ID)).willReturn(Optional.of(shop));

        assertErrorCode(() -> adminShopApplicationService.approve(ADMIN, SHOP_ID),
                ErrorCode.ADMIN_SHOP_APPLICATION_NOT_PENDING);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("신청이 없으면 거절 이벤트를 발행하지 않는다")
    void reject_missingShop_doesNotPublish() {
        given(shopAccountRepository.findByIdForUpdate(SHOP_ID)).willReturn(Optional.empty());

        assertErrorCode(() -> adminShopApplicationService.reject(ADMIN, SHOP_ID, "사유"),
                ErrorCode.ADMIN_SHOP_APPLICATION_NOT_FOUND);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("관리자가 아니면 승인 이벤트를 발행하지 않는다")
    void approve_notAdmin_doesNotPublish() {
        assertErrorCode(() -> adminShopApplicationService.approve(TestUser.OWNER_1.toAuthUser(), SHOP_ID),
                ErrorCode.FORBIDDEN);
        verify(eventPublisher, never()).publishEvent(any());
        verify(shopAccountRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("승인 취소와 거절 취소는 결과 문자 이벤트를 발행하지 않는다")
    void cancel_doesNotPublishResultEvent() {
        ShopAccount approved = activeShop();
        given(shopAccountRepository.findByIdForUpdate(SHOP_ID)).willReturn(Optional.of(approved));
        given(reservationRepository.findActiveOverlapping(any(), any(), any())).willReturn(List.of());
        given(shopCategoryRepository.findById(1L)).willReturn(Optional.empty());

        adminShopApplicationService.cancelApproval(ADMIN, SHOP_ID, "잘못 승인했어요.");

        verify(eventPublisher, never()).publishEvent(any());

        ShopAccount rejected = pendingShop();
        rejected.reject("사유");
        given(shopAccountRepository.findByIdForUpdate(SHOP_ID)).willReturn(Optional.of(rejected));

        adminShopApplicationService.cancelRejection(ADMIN, SHOP_ID);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("예약이 있어 승인 취소를 막으면 이벤트를 발행하지 않는다")
    void cancelApproval_hasReservation_doesNotPublish() {
        ShopAccount approved = activeShop();
        given(shopAccountRepository.findByIdForUpdate(SHOP_ID)).willReturn(Optional.of(approved));
        given(reservationRepository.findActiveOverlapping(any(), any(), any()))
                .willReturn(List.of(occupyingReservation()));

        assertErrorCode(() -> adminShopApplicationService.cancelApproval(ADMIN, SHOP_ID, "잘못 승인했어요."),
                ErrorCode.ADMIN_SHOP_APPLICATION_HAS_RESERVATION);
        assertThat(approved.getStatus()).isEqualTo(ShopAccountStatus.ACTIVE);
        verify(eventPublisher, never()).publishEvent(any());
    }

    // 가입 builder 는 상태를 ACTIVE 로 넣으므로, 승인 대기 신청은 여기서 되돌린다
    private static ShopAccount pendingShop() {
        ShopAccount shop = activeShop();
        shop.restoreToPending();
        return shop;
    }

    private static ShopAccount activeShop() {
        ShopAccount shop = ShopAccount.builder()
                .loginId("pretty-nail")
                .passwordHash("hash")
                .categoryId(1L)
                .name("예쁜네일")
                .regionCode("SEOUL")
                .address("서울시 마포구")
                .latitude(new BigDecimal("37.5500000"))
                .longitude(new BigDecimal("126.9100000"))
                .phone("010-1234-5678")
                .build();
        ReflectionTestUtils.setField(shop, "shopId", SHOP_ID);
        return shop;
    }

    private static Reservation occupyingReservation() {
        LocalDateTime confirmedAt = LocalDateTime.of(2026, 11, 1, 9, 0);
        Reservation reservation = Reservation.builder()
                .userId(1L)
                .treatmentId(1L)
                .startsAt(LocalDateTime.of(2026, 11, 2, 10, 0))
                .endsAt(LocalDateTime.of(2026, 11, 2, 11, 0))
                .appliedPrice(20_000)
                .deposit(20_000)
                .holdExpiresAt(confirmedAt.plusMinutes(5))
                .build();
        reservation.confirm(20_000, confirmedAt, null);
        return reservation;
    }

    private static void assertErrorCode(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(errorCode);
    }
}
