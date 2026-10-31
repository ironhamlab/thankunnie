package com.ucamp.app.domain.notification.service;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.ucamp.app.domain.cancel.entity.CancelType;
import com.ucamp.app.domain.notification.client.SmsSendResult;
import com.ucamp.app.domain.notification.client.SmsSendStatus;
import com.ucamp.app.domain.notification.client.SolapiSmsClient;
import com.ucamp.app.domain.notification.entity.NotificationInboxCategory;
import com.ucamp.app.domain.notification.repository.RecipientContactRepository;
import com.ucamp.app.domain.notification.repository.RecipientContactRepository.CancelSmsInfo;

/**
 * 예약 취소 안내 문자를 고객에게 한 번 보낸다.
 * 발송에 성공하면 받은 알림에 한 줄 남긴다. 실패해도 다시 보내지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancelSmsService {

    private static final DateTimeFormatter STARTS_AT_TEXT = DateTimeFormatter.ofPattern("M월 d일 HH:mm");

    private static final String CANCELLED_TITLE = "예약이 취소되었어요";

    private final RecipientContactRepository recipientContactRepository;
    private final SolapiSmsClient solapiSmsClient;
    private final NotificationInboxService notificationInboxService;

    /**
     * @param reservationId 취소된 예약 ID
     * @param cancelType    고객 취소와 사장님 취소의 문구를 나눈다
     */
    public void send(Long reservationId, CancelType cancelType) {
        Optional<CancelSmsInfo> found = recipientContactRepository.findCancelSmsInfo(reservationId);
        if (found.isEmpty()) {
            log.warn("취소 문자 생략: 예약 정보 없음 reservationId={}", reservationId);
            return;
        }
        CancelSmsInfo info = found.get();
        if (!StringUtils.hasText(info.phone())) {
            log.warn("취소 문자 생략: 고객 전화번호 없음 reservationId={}", reservationId);
            return;
        }

        String messageBody = buildMessage(info, cancelType);
        SmsSendResult result = solapiSmsClient.send(info.phone(), messageBody);
        if (result.status() == SmsSendStatus.SUCCESS) {
            notificationInboxService.save(
                    info.userId(),
                    NotificationInboxCategory.RESERVATION,
                    CANCELLED_TITLE,
                    messageBody,
                    NotificationInboxService.MY_RESERVATIONS_PATH);
            log.info("취소 문자 발송 성공 reservationId={}, providerMessageId={}",
                    reservationId, result.providerMessageId());
        } else {
            log.warn("취소 문자 발송 {} reservationId={}, reason={}",
                    result.status(), reservationId, result.failReason());
        }
    }

    // 고객 직접 취소: [땡잡은 언니] 예쁜네일 11월 2일 10:00 예약이 취소되었습니다.
    // 매장 사유 취소: [땡잡은 언니] 예쁜네일 11월 2일 10:00 예약이 매장 사정으로 취소되었습니다.
    // 고객 요청 대리 취소: [땡잡은 언니] 예쁜네일 11월 2일 10:00 고객 요청으로 예약이 취소되었습니다.
    String buildMessage(CancelSmsInfo info, CancelType cancelType) {
        return "[땡잡은 언니] " + info.shopName() + " " + info.startsAt().format(STARTS_AT_TEXT)
                + " " + cancelSentence(cancelType);
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
