package com.ucamp.app.domain.reservation.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

/**
 * 매장 예약 현황 API (B-10). 매장은 로그인한 매장 계정으로 정해진다.
 */
@Tag(name = "매장 예약 현황", description = "본인 매장 예약·예약자·결제·취소 상태 조회")
@RestController
@RequestMapping("/api/v1/owner/reservations")
public class OwnerReservationController {

    private final ReservationQueryService reservationQueryService;

    public OwnerReservationController(ReservationQueryService reservationQueryService) {
        this.reservationQueryService = reservationQueryService;
    }

    @Operation(summary = "본인 매장 예약 목록",
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
                reservationQueryService.getOwnerReservations(loginUser, from, to, statuses, page, size)));
    }

    @Operation(summary = "본인 매장 예약 상세", description = "예약자·결제·취소 상태 포함. 다른 매장 예약은 404")
    @GetMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<ReservationDetailResponse>> getReservation(
            @LoginUser AuthUser loginUser, @PathVariable Long reservationId) {
        return ResponseEntity.ok(ApiResponse.success(
                reservationQueryService.getOwnerReservation(loginUser, reservationId)));
    }
}
