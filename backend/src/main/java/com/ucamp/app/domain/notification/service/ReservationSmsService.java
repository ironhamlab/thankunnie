package com.ucamp.app.domain.notification.service;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.ucamp.app.domain.notification.client.SmsSendResult;
import com.ucamp.app.domain.notification.client.SmsSendStatus;
import com.ucamp.app.domain.notification.client.SolapiSmsClient;
import com.ucamp.app.domain.notification.repository.RecipientContactRepository;
import com.ucamp.app.domain.notification.repository.RecipientContactRepository.CancelSmsInfo;

/**
 * 예약 확정 안내 문자를 고객에게 한 번 보낸다.
 * 결과는 DB 에 남기지 않고 로그로만 남긴다. 실패해도 다시 보내지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationSmsService {

    private static final DateTimeFormatter STARTS_AT_TEXT = DateTimeFormatter.ofPattern("M월 d일 HH:mm");

    private final RecipientContactRepository recipientContactRepository;
    private final SolapiSmsClient solapiSmsClient;

    /**
     * @param reservationId 확정된 예약 ID
     * @param paidAmount    결제 금액
     */
    public void send(Long reservationId, Integer paidAmount) {
        Optional<CancelSmsInfo> found = recipientContactRepository.findReservationSmsInfo(reservationId);
        if (found.isEmpty()) {
            log.warn("예약 확정 문자 생략: 예약 정보 없음 reservationId={}", reservationId);
            return;
        }
        CancelSmsInfo info = found.get();
        if (!StringUtils.hasText(info.phone())) {
            log.warn("예약 확정 문자 생략: 고객 전화번호 없음 reservationId={}", reservationId);
            return;
        }

        SmsSendResult result = solapiSmsClient.send(info.phone(), buildMessage(info, paidAmount));
        if (result.status() == SmsSendStatus.SUCCESS) {
            log.info("예약 확정 문자 발송 성공 reservationId={}, providerMessageId={}",
                    reservationId, result.providerMessageId());
        } else {
            log.warn("예약 확정 문자 발송 {} reservationId={}, reason={}",
                    result.status(), reservationId, result.failReason());
        }
    }

    // 예: [땡잡은 언니] 예쁜네일 11월 2일 10:00 예약이 확정되었습니다. 결제 금액: 20,000원
    String buildMessage(CancelSmsInfo info, Integer paidAmount) {
        return "[땡잡은 언니] " + info.shopName() + " " + info.startsAt().format(STARTS_AT_TEXT)
                + " 예약이 확정되었습니다. 결제 금액: " + String.format("%,d", paidAmount) + "원";
    }
}
