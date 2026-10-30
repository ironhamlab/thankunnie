package com.ucamp.app.domain.reservation.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.LoginUser;
import com.ucamp.app.common.response.ApiResponse;
import com.ucamp.app.common.response.PageResponse;
import com.ucamp.app.domain.reservation.dto.response.ReservationDetailResponse;
import com.ucamp.app.domain.reservation.dto.response.ReservationSummaryResponse;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;
import com.ucamp.app.domain.reservation.service.ReservationQueryService;
import com.ucamp.app.domain.reservation.service.ReservationService;

/**
 * 고객 내 예약 API (B-09). 로그인한 고객 본인의 예약만 조회한다.
 */
@Tag(name = "고객 내 예약", description = "본인 예약 목록·상세(결제·취소 상태) 조회")
@RestController
@RequestMapping("/api/v1/my/reservations")
public class MyReservationController {

    private final ReservationQueryService reservationQueryService;
    private final ReservationService reservationService;

    public MyReservationController(ReservationQueryService reservationQueryService,
            ReservationService reservationService) {
        this.reservationQueryService = reservationQueryService;
        this.reservationService = reservationService;
    }

    @Operation(summary = "내 예약 목록",
            description = "from~to 시작 날짜(포함)·상태 필터, 시작 시각 순. 상태를 생략하면 점유 만료(EXPIRED)를 뺀 전체")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ReservationSummaryResponse>>> getReservations(
            @LoginUser AuthUser loginUser,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "status", required = false) List<ReservationStatus> statuses,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                reservationQueryService.getMyReservations(loginUser, from, to, statuses, page, size)));
    }

    @Operation(summary = "내 예약 상세", description = "결제·취소 상태 포함. 다른 고객 예약은 404")
    @GetMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<ReservationDetailResponse>> getReservation(
            @LoginUser AuthUser loginUser, @PathVariable Long reservationId) {
        return ResponseEntity.ok(ApiResponse.success(
                reservationQueryService.getMyReservation(loginUser, reservationId)));
    }

    @Operation(summary = "내 점유 풀기",
            description = "결제 화면을 떠날 때 결제 대기(HOLD) 예약을 EXPIRED 로 바꾼다. 이미 만료됐으면 그대로 성공")
    @PostMapping("/{reservationId}/release")
    public ResponseEntity<Void> releaseHold(@LoginUser AuthUser loginUser, @PathVariable Long reservationId) {
        reservationService.releaseHold(loginUser, reservationId);
        return ResponseEntity.noContent().build();
    }
}
