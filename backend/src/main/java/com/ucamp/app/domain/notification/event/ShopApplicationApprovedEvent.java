package com.ucamp.app.domain.notification.event;

/**
 * 매장 가입이 승인됐음을 알리는 이벤트. AdminShopApplicationService.approve 에서 발행한다.
 * 승인 트랜잭션이 커밋된 뒤에 리스너가 문자를 보낸다.
 *
 * @param shopId 승인된 매장 ID
 */
public record ShopApplicationApprovedEvent(Long shopId) {
}
