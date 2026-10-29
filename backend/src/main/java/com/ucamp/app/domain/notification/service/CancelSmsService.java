package com.ucamp.app.domain.notification.service;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.ucamp.app.domain.cancel.entity.CancelType;
import com.ucamp.app.domain.notification.client.SmsSendResult;
import com.ucamp.app.domain.notification.client.SmsSendStatus;
import com.ucamp.app.domain.notification.client.SolapiSmsClient;
import com.ucamp.app.domain.notification.repository.RecipientContactRepository;
import com.ucamp.app.domain.notification.repository.RecipientContactRepository.CancelSmsInfo;

/**
 * 예약 취소 안내 문자를 고객에게 한 번 보낸다.
 * 결과는 DB 에 남기지 않고 로그로만 남긴다. 실패해도 다시 보내지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancelSmsService {

    private static final DateTimeFormatter STARTS_AT_TEXT = DateTimeFormatter.ofPattern("M월 d일 HH:mm");

    private final RecipientContactRepository recipientContactRepository;
    private final SolapiSmsClient solapiSmsClient;

    /**
     * @param reservationId 취소된 예약 ID
     * @param refundAmount  환불 금액
     * @param cancelType    고객 취소와 사장님 취소의 문구를 나눈다
     */
    public void send(Long reservationId, Integer refundAmount, CancelType cancelType) {
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

        SmsSendResult result = solapiSmsClient.send(info.phone(), buildMessage(info, refundAmount, cancelType));
        if (result.status() == SmsSendStatus.SUCCESS) {
            log.info("취소 문자 발송 성공 reservationId={}, providerMessageId={}",
                    reservationId, result.providerMessageId());
        } else {
            log.warn("취소 문자 발송 {} reservationId={}, reason={}",
                    result.status(), reservationId, result.failReason());
        }
    }

    // 고객 취소: [땡잡은 언니] 예쁜네일 11월 2일 10:00 예약이 취소되었습니다. 환불 금액: 20,000원
    // 사장님 취소: [땡잡은 언니] 예쁜네일 11월 2일 10:00 예약이 매장 사정으로 취소되었습니다. 환불 금액: 20,000원
    String buildMessage(CancelSmsInfo info, Integer refundAmount, CancelType cancelType) {
        String sentence = ownerCancelled(cancelType)
                ? "예약이 매장 사정으로 취소되었습니다."
                : "예약이 취소되었습니다.";
        return "[땡잡은 언니] " + info.shopName() + " " + info.startsAt().format(STARTS_AT_TEXT)
                + " " + sentence + " 환불 금액: " + String.format(Locale.KOREA, "%,d", refundAmount) + "원";
    }

    // 사장님이 직접 취소한 경우(매장 사유, 고객 요청 대리 취소)만 매장 사정 문구를 쓴다
    private boolean ownerCancelled(CancelType cancelType) {
        return cancelType == CancelType.SHOP_FAULT || cancelType == CancelType.CUSTOMER_REQUEST;
    }
}
