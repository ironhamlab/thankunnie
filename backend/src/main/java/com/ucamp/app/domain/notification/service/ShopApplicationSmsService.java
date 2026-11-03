package com.ucamp.app.domain.notification.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.ucamp.app.domain.auth.entity.ShopAccount;
import com.ucamp.app.domain.auth.repository.ShopAccountRepository;
import com.ucamp.app.domain.notification.client.SmsSendResult;
import com.ucamp.app.domain.notification.client.SmsSendStatus;
import com.ucamp.app.domain.notification.client.SolapiSmsClient;

/**
 * 매장 가입 승인·거절 문자를 매장 전화번호로 한 번 보낸다.
 * 승인·거절 트랜잭션이 커밋된 뒤 리스너가 호출한다.
 * 발송에 실패해도 예외를 던지지 않고, 받은 알림함에는 남기지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopApplicationSmsService {

    private final ShopAccountRepository shopAccountRepository;
    private final SolapiSmsClient solapiSmsClient;

    /**
     * 승인된 매장 전화번호로 승인 안내 문자를 보낸다.
     *
     * @param shopId 승인된 매장 ID
     */
    public void sendApproved(Long shopId) {
        ShopAccount shop = findShop(shopId);
        if (shop == null) {
            return;
        }
        send(shopId, shop.getPhone(), approvedMessage(shop.getName()));
    }

    /**
     * 거절된 매장 전화번호로 거절 안내 문자를 보낸다.
     *
     * @param shopId       거절된 매장 ID
     * @param rejectReason 문자에 넣을 거절 사유
     */
    public void sendRejected(Long shopId, String rejectReason) {
        ShopAccount shop = findShop(shopId);
        if (shop == null) {
            return;
        }
        send(shopId, shop.getPhone(), rejectedMessage(shop.getName(), rejectReason));
    }

    // 문자는 이미 커밋된 뒤에 보내므로 매장 행을 잠그지 않는다
    private ShopAccount findShop(Long shopId) {
        Optional<ShopAccount> found = shopAccountRepository.findById(shopId);
        if (found.isEmpty()) {
            log.warn("가입 결과 문자 생략: 매장 정보 없음 shopId={}", shopId);
            return null;
        }
        return found.get();
    }

    private void send(Long shopId, String phone, String messageBody) {
        if (!StringUtils.hasText(phone)) {
            log.warn("가입 결과 문자 생략: 매장 전화번호 없음 shopId={}", shopId);
            return;
        }

        SmsSendResult result = solapiSmsClient.send(phone, messageBody);
        if (result.status() == SmsSendStatus.SUCCESS) {
            log.info("가입 결과 문자 발송 성공 shopId={}, providerMessageId={}",
                    shopId, result.providerMessageId());
            return;
        }
        log.warn("가입 결과 문자 발송 {} shopId={}, reason={}",
                result.status(), shopId, result.failReason());
    }

    // 예: [땡잡은 언니] 예쁜네일 가입이 승인되었습니다.
    private String approvedMessage(String shopName) {
        return "[땡잡은 언니] " + text(shopName) + " 가입이 승인되었습니다.";
    }

    // 예: [땡잡은 언니] 예쁜네일 가입이 거절되었습니다. 사유: 서비스 대상 업종이 아니에요.
    private String rejectedMessage(String shopName, String rejectReason) {
        return "[땡잡은 언니] " + text(shopName) + " 가입이 거절되었습니다. 사유: " + text(rejectReason);
    }

    private String text(String value) {
        return value == null ? "" : value;
    }
}
