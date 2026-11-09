package com.ucamp.app.domain.notification.event;

/**
 * 매장 가입이 거절됐음을 알리는 이벤트. AdminShopApplicationService.reject 에서 발행한다.
 * 거절 트랜잭션이 커밋된 뒤에 리스너가 문자를 보낸다.
 *
 * @param shopId       거절된 매장 ID
 * @param rejectReason 매장에 안내할 거절 사유. 앞뒤 공백은 뺀 값이다
 */
public record ShopApplicationRejectedEvent(Long shopId, String rejectReason) {
}
