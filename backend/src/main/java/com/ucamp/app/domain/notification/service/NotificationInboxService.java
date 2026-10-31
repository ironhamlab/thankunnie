package com.ucamp.app.domain.notification.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.RequiredArgsConstructor;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.domain.notification.dto.response.NotificationInboxResponse;
import com.ucamp.app.domain.notification.entity.NotificationInbox;
import com.ucamp.app.domain.notification.entity.NotificationInboxCategory;
import com.ucamp.app.domain.notification.repository.NotificationInboxRepository;

/**
 * 받은 알림을 저장하고, 로그인한 고객 본인의 목록·안 읽은 수·모두 읽음을 처리한다.
 * 저장이나 모두 읽음이 반영되면 그 회원의 알림 스트림에 새 안 읽은 수를 보낸다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationInboxService {

    /** 내 예약 화면. 예약 확정·취소 알림이 이동하는 경로다. */
    public static final String MY_RESERVATIONS_PATH = "/my-reservations";

    /** 타임세일 목록. 매장이 여러 곳인 묶음 알림이 이동하는 경로다. */
    public static final String TIME_SALE_PATH = "/?sale=1";

    private final NotificationInboxRepository notificationInboxRepository;
    private final Clock clock;
    private final NotificationInboxConnections connections;

    /**
     * 받은 알림 한 줄을 저장한다. 읽은 시각은 비워 둔다.
     * 예약 확정·취소 문자는 그 트랜잭션이 커밋된 뒤에 호출한다.
     * 그때 남은 트랜잭션에 붙으면 이 줄이 커밋되지 않으므로, 알림함만 새 트랜잭션으로 저장한다.
     *
     * @param userId   알림을 받은 회원
     * @param category 예약 또는 관심
     * @param title    종 목록에 보이는 제목
     * @param body     보낸 문자와 같은 본문
     * @param linkPath 누르면 이동할 경로. 없으면 null
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(Long userId, NotificationInboxCategory category, String title, String body, String linkPath) {
        notificationInboxRepository.save(NotificationInbox.builder()
                .userId(userId)
                .category(category)
                .title(title)
                .body(body)
                .linkPath(linkPath)
                .build());
        publishUnreadCount(userId);
    }

    /**
     * 로그인한 고객 본인의 받은 알림을 최신 50건까지 조회한다.
     * category 가 없으면 전체 종류에서 50건을 반환한다.
     *
     * @param loginUser 로그인한 고객
     * @param category  예약 또는 관심. null 이면 전체
     * @return 최신순 받은 알림. 최대 50건
     */
    public List<NotificationInboxResponse> getMyInbox(AuthUser loginUser, NotificationInboxCategory category) {
        loginUser.requireRole(UserRole.CUSTOMER);
        List<NotificationInbox> inboxes;
        if (category == null) {
            inboxes = notificationInboxRepository.findTop50ByUserIdOrderByCreatedAtDescIdDesc(loginUser.userId());
        } else {
            inboxes = notificationInboxRepository.findTop50ByUserIdAndCategoryOrderByCreatedAtDescIdDesc(
                    loginUser.userId(), category);
        }
        return inboxes.stream().map(NotificationInboxResponse::from).toList();
    }

    /**
     * 로그인한 고객 본인의 안 읽은 알림 수를 반환한다.
     * 스트림을 열 때와 모두 읽음 처리 후 개수를 확인할 때 쓴다.
     *
     * @param loginUser 로그인한 고객
     * @return 안 읽은 알림 수
     */
    public long countUnread(AuthUser loginUser) {
        loginUser.requireRole(UserRole.CUSTOMER);
        return notificationInboxRepository.countByUserIdAndReadAtIsNull(loginUser.userId());
    }

    /**
     * 로그인한 고객 본인의 안 읽은 알림을 모두 읽음으로 바꾼다.
     * 이미 읽은 알림의 시각은 바꾸지 않는다.
     *
     * @param loginUser 로그인한 고객
     * @return 처리 후 안 읽은 알림 수
     */
    @Transactional
    public long markAllRead(AuthUser loginUser) {
        loginUser.requireRole(UserRole.CUSTOMER);
        LocalDateTime now = LocalDateTime.now(clock);
        List<NotificationInbox> unread = notificationInboxRepository.findByUserIdAndReadAtIsNull(loginUser.userId());
        for (NotificationInbox inbox : unread) {
            inbox.markRead(now);
        }
        long unreadCount = countUnread(loginUser);
        publishUnreadCount(loginUser.userId());
        return unreadCount;
    }

    /**
     * 안 읽은 수를 그 회원의 스트림으로 보낸다.
     * 트랜잭션 안이면 커밋된 뒤에 센다. 저장이 취소되면 보내지 않는다.
     *
     * @param userId 알림을 받은 회원
     */
    private void publishUnreadCount(Long userId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            connections.send(userId, notificationInboxRepository.countByUserIdAndReadAtIsNull(userId));
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                connections.send(userId, notificationInboxRepository.countByUserIdAndReadAtIsNull(userId));
            }
        });
    }

    /**
     * 매장 상세 경로. 빈자리·임박·초임박 한 건이 이동하는 경로다.
     *
     * @param shopId 매장 ID
     * @return 매장 상세 경로
     */
    public static String shopPath(Long shopId) {
        return "/shops/" + shopId;
    }
}
