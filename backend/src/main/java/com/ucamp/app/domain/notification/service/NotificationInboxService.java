package com.ucamp.app.domain.notification.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.RequiredArgsConstructor;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.notification.dto.response.NotificationInboxResponse;
import com.ucamp.app.domain.notification.entity.NotificationInbox;
import com.ucamp.app.domain.notification.entity.NotificationInboxCategory;
import com.ucamp.app.domain.notification.repository.NotificationInboxRepository;

/**
 * 받은 알림을 저장하고, 로그인한 고객·사장님 본인의 목록·안 읽은 수·모두 읽음을 처리한다.
 * 고객은 회원 ID, 사장님은 매장 ID로 찾는다.
 * 고객 알림이 반영되면 그 회원 스트림에, 사장님 알림이 반영되면 그 매장 스트림에 안 읽은 수를 보낸다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationInboxService {

    /** 내 예약 화면. 확정·취소 알림은 각 서비스에서 탭과 예약 번호를 붙여 쓴다. */
    public static final String MY_RESERVATIONS_PATH = "/my-reservations";

    /** 사장님 예약 현황. 예약 번호가 붙은 경로는 ownerBookingsPath 를 쓴다. */
    public static final String OWNER_BOOKINGS_PATH = "/owner/bookings";

    /** 매장 링크의 시작 시각. 2026-11-02T15:00:00 처럼 초까지 두고 오프셋은 붙이지 않는다. */
    private static final DateTimeFormatter SLOT_START_PARAM =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

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
     * 사장님 받은 알림 한 줄을 저장한다. 문자를 보내지 않는다.
     * 예약 확정·취소 트랜잭션이 커밋된 뒤에 호출한다.
     * 알림함만 새 트랜잭션으로 저장하고, 그 매장 스트림에만 안 읽은 수를 보낸다.
     *
     * @param shopId   알림을 받은 매장
     * @param category 예약 또는 관심
     * @param title    종 목록에 보이는 제목
     * @param body     종 목록에 보이는 본문
     * @param linkPath 누르면 이동할 경로. 없으면 null
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveForShop(Long shopId, NotificationInboxCategory category, String title, String body,
            String linkPath) {
        notificationInboxRepository.save(NotificationInbox.builder()
                .shopId(shopId)
                .category(category)
                .title(title)
                .body(body)
                .linkPath(linkPath)
                .build());
        publishShopUnreadCount(shopId);
    }

    /**
     * 로그인한 고객 또는 사장님 본인의 받은 알림을 최신 50건까지 조회한다.
     * 고객은 회원 ID, 사장님은 매장 ID로 찾는다.
     * category 가 없으면 그 사람의 전체 종류에서 50건을 반환한다.
     * 사장님에게 관심 알림은 없으므로 INTEREST 를 주면 빈 목록이다.
     *
     * @param loginUser 로그인한 고객 또는 사장님
     * @param category  예약 또는 관심. null 이면 전체
     * @return 최신순 받은 알림. 최대 50건
     */
    public List<NotificationInboxResponse> getMyInbox(AuthUser loginUser, NotificationInboxCategory category) {
        loginUser.requireRole(UserRole.CUSTOMER, UserRole.OWNER);
        List<NotificationInbox> inboxes;
        if (loginUser.hasRole(UserRole.OWNER)) {
            inboxes = findOwnerInbox(loginUser.shopId(), category);
        } else {
            inboxes = findCustomerInbox(loginUser.userId(), category);
        }
        return inboxes.stream().map(NotificationInboxResponse::from).toList();
    }

    /**
     * 로그인한 고객 또는 사장님 본인의 안 읽은 알림 수를 반환한다.
     * 스트림을 열 때와 모두 읽음 처리 후 개수를 확인할 때 쓴다.
     *
     * @param loginUser 로그인한 고객 또는 사장님
     * @return 안 읽은 알림 수
     */
    public long countUnread(AuthUser loginUser) {
        loginUser.requireRole(UserRole.CUSTOMER, UserRole.OWNER);
        if (loginUser.hasRole(UserRole.OWNER)) {
            return notificationInboxRepository.countByShopIdAndReadAtIsNull(loginUser.shopId());
        }
        return notificationInboxRepository.countByUserIdAndReadAtIsNull(loginUser.userId());
    }

    /**
     * 로그인한 고객 또는 사장님 본인의 안 읽은 알림을 모두 읽음으로 바꾼다.
     * 이미 읽은 알림의 시각은 바꾸지 않는다.
     * 고객 읽음은 회원 스트림에, 사장님 읽음은 매장 스트림에만 보낸다.
     *
     * @param loginUser 로그인한 고객 또는 사장님
     * @return 처리 후 안 읽은 알림 수
     */
    @Transactional
    public long markAllRead(AuthUser loginUser) {
        loginUser.requireRole(UserRole.CUSTOMER, UserRole.OWNER);
        LocalDateTime now = LocalDateTime.now(clock);
        List<NotificationInbox> unread;
        if (loginUser.hasRole(UserRole.OWNER)) {
            unread = notificationInboxRepository.findByShopIdAndReadAtIsNull(loginUser.shopId());
        } else {
            unread = notificationInboxRepository.findByUserIdAndReadAtIsNull(loginUser.userId());
        }
        for (NotificationInbox inbox : unread) {
            inbox.markRead(now);
        }
        return publishAndCountUnread(loginUser);
    }

    /**
     * 로그인한 고객 또는 사장님 본인의 받은 알림 한 건을 읽음으로 바꾼다.
     * 이미 읽은 알림의 시각은 바꾸지 않는다.
     * 없거나 다른 사람·다른 매장 알림이면 없는 것으로 본다.
     *
     * @param loginUser 로그인한 고객 또는 사장님
     * @param inboxId   읽음으로 바꿀 받은 알림 ID
     * @return 처리 후 안 읽은 알림 수
     */
    @Transactional
    public long markRead(AuthUser loginUser, Long inboxId) {
        loginUser.requireRole(UserRole.CUSTOMER, UserRole.OWNER);
        NotificationInbox inbox = notificationInboxRepository.findById(inboxId)
                .filter(found -> belongsTo(loginUser, found))
                .orElseThrow(() -> new BusinessException(ErrorCode.INBOX_NOT_FOUND));
        inbox.markRead(LocalDateTime.now(clock));
        return publishAndCountUnread(loginUser);
    }

    private boolean belongsTo(AuthUser loginUser, NotificationInbox inbox) {
        if (loginUser.hasRole(UserRole.OWNER)) {
            return loginUser.shopId().equals(inbox.getShopId());
        }
        return loginUser.userId().equals(inbox.getUserId());
    }

    private long publishAndCountUnread(AuthUser loginUser) {
        long unreadCount = countUnread(loginUser);
        if (loginUser.hasRole(UserRole.OWNER)) {
            publishShopUnreadCount(loginUser.shopId());
        } else {
            publishUnreadCount(loginUser.userId());
        }
        return unreadCount;
    }

    private List<NotificationInbox> findCustomerInbox(Long userId, NotificationInboxCategory category) {
        if (category == null) {
            return notificationInboxRepository.findTop50ByUserIdOrderByCreatedAtDescIdDesc(userId);
        }
        return notificationInboxRepository.findTop50ByUserIdAndCategoryOrderByCreatedAtDescIdDesc(userId, category);
    }

    private List<NotificationInbox> findOwnerInbox(Long shopId, NotificationInboxCategory category) {
        if (category == NotificationInboxCategory.INTEREST) {
            return List.of();
        }
        if (category == null) {
            return notificationInboxRepository.findTop50ByShopIdOrderByCreatedAtDescIdDesc(shopId);
        }
        return notificationInboxRepository.findTop50ByShopIdAndCategoryOrderByCreatedAtDescIdDesc(shopId, category);
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
     * 안 읽은 수를 그 매장의 스트림으로 보낸다.
     * 회원 연결과 매장 ID가 같아도 회원 화면에는 보내지 않는다.
     * 트랜잭션 안이면 커밋된 뒤에 센다. 저장이 취소되면 보내지 않는다.
     *
     * @param shopId 알림을 받은 매장
     */
    private void publishShopUnreadCount(Long shopId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            connections.sendToShop(shopId, notificationInboxRepository.countByShopIdAndReadAtIsNull(shopId));
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                connections.sendToShop(shopId, notificationInboxRepository.countByShopIdAndReadAtIsNull(shopId));
            }
        });
    }

    /**
     * 매장 상세 경로. 그 회원에게 맞는 시술을 못 찾으면 이 주소만 둔다.
     *
     * @param shopId 매장 ID
     * @return 매장 상세 경로
     */
    public static String shopPath(Long shopId) {
        return "/shops/" + shopId;
    }

    /**
     * 업장 알림이 이동하는 매장 상세 경로. 고른 시술과 빈자리 시작 시각을 붙인다.
     * 예: /shops/1?treatmentId=10&startsAt=2026-11-02T15:00:00
     *
     * @param shopId       매장 ID
     * @param treatmentId  그 회원 알림 중 이 시각에 예약되는 시술. 여러 개면 ID가 가장 작은 것
     * @param slotStartsAt 빈자리 시작 시각
     * @return 시술과 시작 시각이 붙은 매장 상세 경로. 시술이나 시각이 없으면 매장 주소만 반환
     */
    public static String shopBookingPath(Long shopId, Long treatmentId, LocalDateTime slotStartsAt) {
        if (treatmentId == null || slotStartsAt == null) {
            return shopPath(shopId);
        }
        return shopPath(shopId)
                + "?treatmentId=" + treatmentId
                + "&startsAt=" + slotStartsAt.format(SLOT_START_PARAM);
    }

    /**
     * 사장님 예약 알림이 이동하는 경로. 예약 현황에서 그 예약 상세가 열린다.
     * 예: /owner/bookings?reservationId=100
     *
     * @param reservationId 상세로 열 예약 ID
     * @return 예약 번호가 붙은 예약 현황 경로
     */
    public static String ownerBookingsPath(Long reservationId) {
        return OWNER_BOOKINGS_PATH + "?reservationId=" + reservationId;
    }

    /**
     * 카테고리·지역 알림이 이동하는 빈자리 목록 경로.
     * 예: /?view=list&category=1&region=11680&date=2026-11-02
     *
     * @param categoryId 알림에 고른 카테고리 ID
     * @param regionCode 알림에 고른 지역코드
     * @param date       빈자리가 있는 날. null 이면 날짜 없이 보낸다
     * @return 그 카테고리·지역·날짜로 좁힌 빈자리 목록 경로
     */
    public static String categoryRegionListPath(Long categoryId, String regionCode, LocalDate date) {
        String path = "/?view=list&category=" + categoryId + "&region=" + regionCode;
        if (date != null) {
            path += "&date=" + date;
        }
        return path;
    }
}
