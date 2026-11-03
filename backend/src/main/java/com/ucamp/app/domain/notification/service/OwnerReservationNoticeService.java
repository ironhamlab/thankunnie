package com.ucamp.app.domain.notification.service;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.ucamp.app.domain.cancel.entity.CancelType;
import com.ucamp.app.domain.notification.entity.NotificationInboxCategory;
import com.ucamp.app.domain.notification.repository.RecipientContactRepository;
import com.ucamp.app.domain.notification.repository.RecipientContactRepository.OwnerReservationNotice;

/**
 * 예약 확정·취소 알림을 사장님 종 목록에 남긴다.
 * 문자는 보내지 않는다. 고객 문자 성공 여부와 상관없이 저장한다.
 * 사장님이 직접 취소한 건도 남긴다. 취소가 끝난 기록이라 종에서 빼지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OwnerReservationNoticeService {

    private static final DateTimeFormatter STARTS_AT_TEXT = DateTimeFormatter.ofPattern("M월 d일 HH:mm");

    private static final String CONFIRMED_TITLE = "예약이 확정되었어요";

    private static final String CANCELLED_TITLE = "예약이 취소되었어요";

    private final RecipientContactRepository recipientContactRepository;
    private final NotificationInboxService notificationInboxService;

    /**
     * 확정된 예약을 사장님 받은 알림에 한 줄 남긴다.
     *
     * @param reservationId 확정된 예약 ID
     */
    public void notifyConfirmed(Long reservationId) {
        Optional<OwnerReservationNotice> found = recipientContactRepository.findOwnerReservationNotice(reservationId);
        if (found.isEmpty()) {
            log.warn("사장님 예약 확정 알림 생략: 예약 정보 없음 reservationId={}", reservationId);
            return;
        }
        OwnerReservationNotice notice = found.get();
        notificationInboxService.saveForShop(
                notice.shopId(),
                NotificationInboxCategory.RESERVATION,
                CONFIRMED_TITLE,
                confirmedBody(notice),
                NotificationInboxService.ownerBookingsPath(reservationId));
    }

    /**
     * 취소된 예약을 사장님 받은 알림에 한 줄 남긴다.
     * 고객 취소, 매장 사정 취소, 고객 요청 대리 취소를 모두 남긴다.
     *
     * @param reservationId 취소된 예약 ID
     * @param cancelType    취소 유형. 문구만 나눈다
     */
    public void notifyCancelled(Long reservationId, CancelType cancelType) {
        Optional<OwnerReservationNotice> found = recipientContactRepository.findOwnerReservationNotice(reservationId);
        if (found.isEmpty()) {
            log.warn("사장님 예약 취소 알림 생략: 예약 정보 없음 reservationId={}", reservationId);
            return;
        }
        OwnerReservationNotice notice = found.get();
        notificationInboxService.saveForShop(
                notice.shopId(),
                NotificationInboxCategory.RESERVATION,
                CANCELLED_TITLE,
                cancelledBody(notice, cancelType),
                NotificationInboxService.ownerBookingsPath(reservationId));
    }

    // 예: 김고객 님 · 젤네일 · 11월 2일 10:00 예약이 확정되었습니다.
    String confirmedBody(OwnerReservationNotice notice) {
        return reservationSummary(notice) + " 예약이 확정되었습니다.";
    }

    // 고객 직접 취소: 김고객 님 · 젤네일 · 11월 2일 10:00 예약이 취소되었습니다.
    // 매장 사유 취소: 김고객 님 · 젤네일 · 11월 2일 10:00 예약이 매장 사정으로 취소되었습니다.
    // 고객 요청 대리 취소: 김고객 님 · 젤네일 · 11월 2일 10:00 고객 요청으로 예약이 취소되었습니다.
    String cancelledBody(OwnerReservationNotice notice, CancelType cancelType) {
        return reservationSummary(notice) + " " + cancelSentence(cancelType);
    }

    // 고객명·시술명·시각을 나눠 쓴다. 시술명에 공백이 있어도 고객명과 헷갈리지 않게 한다.
    private String reservationSummary(OwnerReservationNotice notice) {
        return notice.customerName() + " 님 · " + notice.treatmentName() + " · "
                + notice.startsAt().format(STARTS_AT_TEXT);
    }

    private String cancelSentence(CancelType cancelType) {
        if (cancelType == CancelType.SHOP_FAULT) {
            return "예약이 매장 사정으로 취소되었습니다.";
        }
        if (cancelType == CancelType.CUSTOMER_REQUEST) {
            return "고객 요청으로 예약이 취소되었습니다.";
        }
        return "예약이 취소되었습니다.";
    }
}
