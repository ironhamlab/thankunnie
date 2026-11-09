package com.ucamp.app.domain.notification.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.LoginUser;
import com.ucamp.app.common.response.ApiResponse;
import com.ucamp.app.domain.notification.dto.request.NotificationSubscriptionCreateRequest;
import com.ucamp.app.domain.notification.dto.request.NotificationSubscriptionUpdateRequest;
import com.ucamp.app.domain.notification.dto.response.NotificationInboxResponse;
import com.ucamp.app.domain.notification.dto.response.NotificationSubscriptionResponse;
import com.ucamp.app.domain.notification.entity.NotificationInboxCategory;
import com.ucamp.app.domain.notification.service.NotificationInboxService;
import com.ucamp.app.domain.notification.service.NotificationInboxStreamService;
import com.ucamp.app.domain.notification.service.NotificationService;

/**
 * 알림 API.
 * 알림 등록·조회·수정·해제는 고객만 사용할 수 있다.
 * 받은 알림 목록·한 건 읽음·모두 읽음·알림 스트림은 고객과 사장님이 사용할 수 있다.
 */
@Tag(name = "Notification", description = "알림")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationInboxService notificationInboxService;
    private final NotificationInboxStreamService notificationInboxStreamService;

    /**
     * 업장 알림 또는 카테고리·지역 알림을 등록한다.
     *
     * @param loginUser 로그인한 고객
     * @param request   알림 등록 요청
     * @return 등록된 알림
     */
    @Operation(summary = "알림 등록",
            description = "STORE 찜은 shopId 만, 알림은 shopId 와 고객이 고른 treatmentId 와 희망 시간을 보낸다. CATEGORY_REGION 은 categoryId·regionCode 와 희망 시작·종료 시각을 보낸다")
    @PostMapping("/subscriptions")
    public ResponseEntity<ApiResponse<NotificationSubscriptionResponse>> subscribe(
            @LoginUser AuthUser loginUser,
            @Valid @RequestBody NotificationSubscriptionCreateRequest request) {
        NotificationSubscriptionResponse response = notificationService.subscribe(loginUser, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    /**
     * 해제하지 않은 내 알림 목록을 최신 등록 순으로 조회한다.
     *
     * @param loginUser 로그인한 고객
     * @return 활성 알림 목록
     */
    @Operation(summary = "내 알림 목록 조회", description = "해제하지 않은 알림을 최신 등록 순으로 반환한다")
    @GetMapping("/subscriptions")
    public ResponseEntity<ApiResponse<List<NotificationSubscriptionResponse>>> getMySubscriptions(
            @LoginUser AuthUser loginUser) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getMySubscriptions(loginUser)));
    }

    /**
     * 본인의 알림에서 대상과 희망 시간을 수정한다. 알림 유형은 유지한다.
     *
     * @param loginUser      로그인한 고객
     * @param subscriptionId 알림 ID
     * @param request        알림 수정 요청
     * @return 수정된 알림
     */
    @Operation(summary = "알림 수정", description = "알림 유형은 바꿀 수 없으며, 기존 유형에 맞는 대상과 희망 시각을 보낸다")
    @PutMapping("/subscriptions/{subscriptionId}")
    public ResponseEntity<ApiResponse<NotificationSubscriptionResponse>> updateSubscription(
            @LoginUser AuthUser loginUser,
            @PathVariable Long subscriptionId,
            @Valid @RequestBody NotificationSubscriptionUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.updateSubscription(loginUser, subscriptionId, request)));
    }

    /**
     * 본인의 알림을 해제한다. 해제한 알림은 목록과 알림 대상에서 빠진다.
     *
     * @param loginUser      로그인한 고객
     * @param subscriptionId 알림 ID
     * @return 본문 없는 204 응답
     */
    @Operation(summary = "알림 해제", description = "해제한 알림은 목록과 알림 대상에서 제외된다")
    @DeleteMapping("/subscriptions/{subscriptionId}")
    public ResponseEntity<Void> unsubscribe(@LoginUser AuthUser loginUser, @PathVariable Long subscriptionId) {
        notificationService.unsubscribe(loginUser, subscriptionId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 본인의 받은 알림을 최신 50건까지 조회한다. category 가 없으면 그 사람의 전체 종류에서 50건을 반환한다.
     * 고객은 회원 알림, 사장님은 매장 알림이다. 사장님의 관심 알림은 빈 목록이다.
     *
     * @param loginUser 로그인한 고객 또는 사장님
     * @param category  예약(RESERVATION) 또는 관심(INTEREST). 없으면 전체
     * @return 최신순 받은 알림. 최대 50건
     */
    @Operation(summary = "받은 알림 목록 조회",
            description = "최신 50건만 반환한다. 고객은 회원 알림, 사장님은 매장 예약 알림이다. category 를 생략하면 그 사람의 전체 종류에서 50건이다. 사장님이 INTEREST 를 주면 빈 목록이다.")
    @GetMapping("/inbox")
    public ResponseEntity<ApiResponse<List<NotificationInboxResponse>>> getInbox(
            @LoginUser AuthUser loginUser,
            @RequestParam(required = false) NotificationInboxCategory category) {
        return ResponseEntity.ok(ApiResponse.success(notificationInboxService.getMyInbox(loginUser, category)));
    }

    /**
     * 고객 또는 사장님의 알림 스트림을 연다. 응답은 JSON 이 아니라 text/event-stream 이다.
     * 연결되면 지금 안 읽은 수를 data 로 한 번 보낸다.
     *
     * @param loginUser 로그인한 고객 또는 사장님
     * @return 열린 스트림
     */
    @Operation(summary = "받은 알림 스트림",
            description = "연결 직후 data 로 지금 안 읽은 수를 한 번 보낸다. 고객은 회원 ID, 사장님은 매장 ID로 연결한다.")
    @GetMapping(value = "/inbox/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@LoginUser AuthUser loginUser) {
        return notificationInboxStreamService.open(loginUser);
    }

    /**
     * 본인의 받은 알림 한 건을 읽음으로 바꾼다. 이미 읽은 시각은 유지한다.
     *
     * @param loginUser 로그인한 고객 또는 사장님
     * @param inboxId   읽음으로 바꿀 받은 알림 ID
     * @return 처리 후 안 읽은 알림 수
     */
    @Operation(summary = "받은 알림 한 건 읽음",
            description = "고객은 본인 회원 알림, 사장님은 본인 매장 알림만 읽음으로 바꾼다. 없거나 남의 알림이면 404다.")
    @PatchMapping("/inbox/{inboxId}/read")
    public ResponseEntity<ApiResponse<Long>> markRead(
            @LoginUser AuthUser loginUser, @PathVariable Long inboxId) {
        return ResponseEntity.ok(ApiResponse.success(notificationInboxService.markRead(loginUser, inboxId)));
    }

    /**
     * 본인의 안 읽은 알림을 모두 읽음으로 바꾼다. 이미 읽은 시각은 유지한다.
     *
     * @param loginUser 로그인한 고객 또는 사장님
     * @return 처리 후 안 읽은 알림 수
     */
    @Operation(summary = "받은 알림 모두 읽음",
            description = "고객은 회원 알림, 사장님은 매장 알림만 읽음으로 바꾼다.")
    @PatchMapping("/inbox/read-all")
    public ResponseEntity<ApiResponse<Long>> markAllRead(
            @LoginUser AuthUser loginUser) {
        return ResponseEntity.ok(ApiResponse.success(notificationInboxService.markAllRead(loginUser)));
    }
}
