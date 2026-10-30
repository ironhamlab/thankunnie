package com.ucamp.app.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CategoryRegionPendingQueueTest {

    private static final Long SUBSCRIPTION_ID = 10L;
    private static final Long OTHER_SUBSCRIPTION_ID = 20L;
    private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 10, 30, 12, 0);

    private CategoryRegionPendingQueue queue;

    @BeforeEach
    void setUp() {
        queue = new CategoryRegionPendingQueue();
    }

    @Test
    @DisplayName("같은 이벤트 번호는 한 번만 넣는다")
    void addPendingEvent_sameEventId_isStoredOnce() {
        queue.addPendingEvent(SUBSCRIPTION_ID, 1L);
        queue.addPendingEvent(SUBSCRIPTION_ID, 1L);

        assertThat(queue.pendingEventIds(SUBSCRIPTION_ID)).containsExactly(1L);
    }

    @Test
    @DisplayName("다른 이벤트 번호는 넣은 순서대로 남긴다")
    void addPendingEvent_differentEventIds_keepsOrder() {
        queue.addPendingEvent(SUBSCRIPTION_ID, 1L);
        queue.addPendingEvent(SUBSCRIPTION_ID, 2L);
        queue.addPendingEvent(SUBSCRIPTION_ID, 1L);

        assertThat(queue.pendingEventIds(SUBSCRIPTION_ID)).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("구독마다 마지막 발송 시각과 이벤트 목록을 따로 둔다")
    void subscriptions_doNotSharePendingEvents() {
        queue.markSent(SUBSCRIPTION_ID, SENT_AT);
        queue.addPendingEvent(SUBSCRIPTION_ID, 1L);
        queue.addPendingEvent(OTHER_SUBSCRIPTION_ID, 2L);

        assertThat(queue.lastSentAt(SUBSCRIPTION_ID)).isEqualTo(SENT_AT);
        assertThat(queue.pendingEventIds(SUBSCRIPTION_ID)).containsExactly(1L);
        assertThat(queue.lastSentAt(OTHER_SUBSCRIPTION_ID)).isNull();
        assertThat(queue.pendingEventIds(OTHER_SUBSCRIPTION_ID)).containsExactly(2L);
    }

    @Test
    @DisplayName("마지막 발송 시각을 적어도 아직 안 보낸 이벤트는 그대로다")
    void markSent_doesNotClearPendingEvents() {
        queue.addPendingEvent(SUBSCRIPTION_ID, 1L);

        queue.markSent(SUBSCRIPTION_ID, SENT_AT);

        assertThat(queue.lastSentAt(SUBSCRIPTION_ID)).isEqualTo(SENT_AT);
        assertThat(queue.pendingEventIds(SUBSCRIPTION_ID)).containsExactly(1L);
    }

    @Test
    @DisplayName("이벤트 목록만 비우고 마지막 발송 시각은 남긴다")
    void clearPendingEvents_keepsLastSentAt() {
        queue.markSent(SUBSCRIPTION_ID, SENT_AT);
        queue.addPendingEvent(SUBSCRIPTION_ID, 1L);
        queue.addPendingEvent(SUBSCRIPTION_ID, 2L);

        queue.clearPendingEvents(SUBSCRIPTION_ID);

        assertThat(queue.pendingEventIds(SUBSCRIPTION_ID)).isEmpty();
        assertThat(queue.lastSentAt(SUBSCRIPTION_ID)).isEqualTo(SENT_AT);
    }

    @Test
    @DisplayName("비운 뒤에는 같은 이벤트 번호를 다시 넣을 수 있다")
    void addPendingEvent_afterClear_acceptsTheSameEventAgain() {
        queue.addPendingEvent(SUBSCRIPTION_ID, 1L);
        queue.clearPendingEvents(SUBSCRIPTION_ID);

        queue.addPendingEvent(SUBSCRIPTION_ID, 1L);

        assertThat(queue.pendingEventIds(SUBSCRIPTION_ID)).containsExactly(1L);
    }

    @Test
    @DisplayName("아직 없는 구독은 마지막 발송 시각이 없고 이벤트 목록도 비어 있다")
    void unknownSubscription_returnsEmpty() {
        assertThat(queue.lastSentAt(SUBSCRIPTION_ID)).isNull();
        assertThat(queue.pendingEventIds(SUBSCRIPTION_ID)).isEmpty();

        queue.clearPendingEvents(SUBSCRIPTION_ID);

        assertThat(queue.lastSentAt(SUBSCRIPTION_ID)).isNull();
    }

    @Test
    @DisplayName("밖에서 받은 목록을 고쳐도 큐 안 목록은 바뀌지 않는다")
    void pendingEventIds_returnsACopy() {
        queue.addPendingEvent(SUBSCRIPTION_ID, 1L);

        List<Long> shown = queue.pendingEventIds(SUBSCRIPTION_ID);
        assertThatThrownBy(() -> shown.add(9L)).isInstanceOf(UnsupportedOperationException.class);

        assertThat(queue.pendingEventIds(SUBSCRIPTION_ID)).containsExactly(1L);
    }

    @Test
    @DisplayName("구독 번호나 이벤트 번호, 발송 시각이 없으면 적지 않는다")
    void nullInput_isIgnored() {
        queue.markSent(null, SENT_AT);
        queue.markSent(SUBSCRIPTION_ID, null);
        queue.addPendingEvent(null, 1L);
        queue.addPendingEvent(SUBSCRIPTION_ID, null);

        assertThat(queue.lastSentAt(SUBSCRIPTION_ID)).isNull();
        assertThat(queue.pendingEventIds(SUBSCRIPTION_ID)).isEmpty();
        assertThat(queue.pendingEventIds(null)).isEmpty();
    }

    @Test
    @DisplayName("전체 비우기는 구독에 적어 둔 시각과 이벤트 번호를 모두 지운다")
    void clear_removesEverySubscription() {
        queue.markSent(SUBSCRIPTION_ID, SENT_AT);
        queue.addPendingEvent(SUBSCRIPTION_ID, 1L);
        queue.addPendingEvent(OTHER_SUBSCRIPTION_ID, 2L);

        queue.clear();

        assertThat(queue.lastSentAt(SUBSCRIPTION_ID)).isNull();
        assertThat(queue.pendingEventIds(SUBSCRIPTION_ID)).isEmpty();
        assertThat(queue.pendingEventIds(OTHER_SUBSCRIPTION_ID)).isEmpty();
    }

    @Test
    @DisplayName("메모장에 있는 구독 번호를 돌려준다")
    void subscriptionIds_returnsStoredSubscriptions() {
        queue.markSent(SUBSCRIPTION_ID, SENT_AT);
        queue.addPendingEvent(OTHER_SUBSCRIPTION_ID, 2L);

        assertThat(queue.subscriptionIds()).containsExactlyInAnyOrder(SUBSCRIPTION_ID, OTHER_SUBSCRIPTION_ID);
    }
}
