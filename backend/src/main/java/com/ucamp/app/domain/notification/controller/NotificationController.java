package com.ucamp.app.domain.notification.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.LoginUser;
import com.ucamp.app.common.response.ApiResponse;
import com.ucamp.app.domain.notification.dto.request.NotificationSubscriptionCreateRequest;
import com.ucamp.app.domain.notification.dto.request.NotificationSubscriptionUpdateRequest;
import com.ucamp.app.domain.notification.dto.response.NotificationSubscriptionResponse;
import com.ucamp.app.domain.notification.service.NotificationService;

/**
 * 알림 구독 API.
 * 업장 구독과 카테고리·지역 구독의 등록, 목록 조회, 수정, 해제를 제공한다. 고객만 사용할 수 있다.
 */
@Tag(name = "Notification", description = "알림 구독")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 업장 구독 또는 카테고리·지역 구독을 등록한다.
     *
     * @param loginUser 로그인한 고객
     * @param request   구독 등록 요청
     * @return 등록된 구독
     */
    @Operation(summary = "알림 구독 등록",
            description = "매장 ID는 항상 보내고, CATEGORY_REGION 만 categoryName·regionName 과 희망 시작·종료 시각을 보낸다")
    @PostMapping("/subscriptions")
    public ResponseEntity<ApiResponse<NotificationSubscriptionResponse>> subscribe(
            @LoginUser AuthUser loginUser,
            @Valid @RequestBody NotificationSubscriptionCreateRequest request) {
        NotificationSubscriptionResponse response = notificationService.subscribe(loginUser, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    /**
     * 해제하지 않은 내 구독 목록을 최신 등록 순으로 조회한다.
     *
     * @param loginUser 로그인한 고객
     * @return 활성 구독 목록
     */
    @Operation(summary = "내 알림 구독 목록 조회", description = "해제하지 않은 구독을 최신 등록 순으로 반환한다")
    @GetMapping("/subscriptions")
    public ResponseEntity<ApiResponse<List<NotificationSubscriptionResponse>>> getMySubscriptions(
            @LoginUser AuthUser loginUser) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getMySubscriptions(loginUser)));
    }

    /**
     * 본인의 구독에서 대상과 희망 시간을 수정한다. 구독 유형은 유지한다.
     *
     * @param loginUser      로그인한 고객
     * @param subscriptionId 구독 ID
     * @param request        구독 수정 요청
     * @return 수정된 구독
     */
    @Operation(summary = "알림 구독 수정", description = "구독 유형은 바꿀 수 없으며, 기존 유형에 맞는 대상과 희망 시각을 보낸다")
    @PutMapping("/subscriptions/{subscriptionId}")
    public ResponseEntity<ApiResponse<NotificationSubscriptionResponse>> updateSubscription(
            @LoginUser AuthUser loginUser,
            @PathVariable Long subscriptionId,
            @Valid @RequestBody NotificationSubscriptionUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.updateSubscription(loginUser, subscriptionId, request)));
    }

    /**
     * 본인의 구독을 해제한다. 해제한 구독은 목록과 알림 대상에서 빠진다.
     *
     * @param loginUser      로그인한 고객
     * @param subscriptionId 구독 ID
     * @return 본문 없는 204 응답
     */
    @Operation(summary = "알림 구독 해제", description = "해제한 구독은 목록과 알림 대상에서 제외된다")
    @DeleteMapping("/subscriptions/{subscriptionId}")
    public ResponseEntity<Void> unsubscribe(@LoginUser AuthUser loginUser, @PathVariable Long subscriptionId) {
        notificationService.unsubscribe(loginUser, subscriptionId);
        return ResponseEntity.noContent().build();
    }
}
