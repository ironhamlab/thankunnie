package com.ucamp.app.domain.reservation.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.LoginUser;
import com.ucamp.app.common.response.ApiResponse;
import com.ucamp.app.domain.reservation.dto.request.ReservationQueueRequest;
import com.ucamp.app.domain.reservation.dto.response.ReservationQueueResponse;
import com.ucamp.app.domain.reservation.service.ReservationQueueService;

/**
 * 예약 대기열 API. 같은 빈자리를 여러 고객이 동시에 누르면 순서대로 한 명씩 예약하게 한다.
 */
@Tag(name = "Reservation", description = "예약")
@RestController
@RequestMapping("/api/v1/reservations/queue")
public class ReservationQueueController {

    private final ReservationQueueService reservationQueueService;

    public ReservationQueueController(ReservationQueueService reservationQueueService) {
        this.reservationQueueService = reservationQueueService;
    }

    /**
     * 빈자리 줄에 서거나 지금 순서를 다시 받는다. 대기 화면이 몇 초마다 다시 부른다.
     *
     * @param loginUser 로그인한 고객
     * @param request   시술 ID, 시작 일시
     * @return 200 과 내 순서 (WAITING, MY_TURN, HOLDING, CLOSED)
     */
    @Operation(summary = "예약 대기열 서기·순서 조회",
            description = "같은 빈자리를 누른 순서대로 번호표를 준다. 한동안 다시 부르지 않으면 줄에서 빠진다")
    @PostMapping
    public ResponseEntity<ApiResponse<ReservationQueueResponse>> joinQueue(
            @LoginUser AuthUser loginUser, @Valid @RequestBody ReservationQueueRequest request) {
        return ResponseEntity.ok(ApiResponse.success(reservationQueueService.joinOrRefresh(loginUser, request)));
    }

    /**
     * 빈자리 줄에서 나간다. 줄에 없어도 성공한다.
     *
     * @param loginUser 로그인한 고객
     * @param request   시술 ID, 시작 일시
     * @return 204
     */
    @Operation(summary = "예약 대기열 나가기", description = "대기를 취소한다. 줄에 없어도 성공한다")
    @PostMapping("/leave")
    public ResponseEntity<Void> leaveQueue(
            @LoginUser AuthUser loginUser, @Valid @RequestBody ReservationQueueRequest request) {
        reservationQueueService.leave(loginUser, request);
        return ResponseEntity.noContent().build();
    }
}
