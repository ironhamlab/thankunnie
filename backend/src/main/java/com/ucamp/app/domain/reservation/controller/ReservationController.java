package com.ucamp.app.domain.reservation.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
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
import com.ucamp.app.domain.reservation.dto.request.ReservationCreateRequest;
import com.ucamp.app.domain.reservation.dto.response.ReservationCreateResponse;
import com.ucamp.app.domain.reservation.service.ReservationQueueService;
import com.ucamp.app.domain.reservation.service.ReservationService;

/**
 * 고객 예약 API. 예약 클릭 시 시간을 점유(HOLD)하는 예약을 만든다.
 * 확정(결제 후)은 결제 도메인이 ReservationService 를 직접 호출하므로 이 컨트롤러에 없다.
 */
@Tag(name = "Reservation", description = "예약")
@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private final ReservationService reservationService;
    private final ReservationQueueService reservationQueueService;

    public ReservationController(ReservationService reservationService,
            ReservationQueueService reservationQueueService) {
        this.reservationService = reservationService;
        this.reservationQueueService = reservationQueueService;
    }

    /**
     * 예약을 생성(점유)한다. 고객만 호출할 수 있다.
     *
     * @param loginUser 로그인한 고객
     * @param request   시술 ID, 시작 일시(30분 단위)
     * @return 201 과 생성된 예약 (적용 판매가, 예약금, 점유 만료 일시 포함)
     */
    @Operation(summary = "예약 생성(점유)",
            description = "고객이 예약을 클릭하면 5분간 점유(HOLD)하는 예약을 만든다. 적용 판매가와 예약금은 이 시점 기준으로 고정한다")
    @PostMapping
    public ResponseEntity<ApiResponse<ReservationCreateResponse>> createReservation(
            @LoginUser AuthUser loginUser, @Valid @RequestBody ReservationCreateRequest request) {
        // 대기열은 순서만 정한다. 같은 시간 두 명 점유는 예약 생성의 잠금이 막는다
        reservationQueueService.requireTurn(loginUser, request);
        ReservationCreateResponse response = reservationService.createReservation(loginUser, request);
        reservationQueueService.completeTurn(loginUser, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }
}
