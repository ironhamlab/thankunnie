package com.ucamp.app.domain.reservation.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.LoginUser;
import com.ucamp.app.common.response.ApiResponse;
import com.ucamp.app.domain.reservation.dto.response.ReservationDashboardResponse;
import com.ucamp.app.domain.reservation.dto.response.ReservationStatsResponse;
import com.ucamp.app.domain.reservation.service.ReservationDashboardService;

/**
 * 매장 메인 대시보드 API. 매장은 로그인한 매장 계정으로 정해진다.
 */
@Tag(name = "매장 대시보드", description = "메인 요약 지표와 기간 통계")
@RestController
@RequestMapping("/api/v1/owner/dashboard")
public class OwnerDashboardController {

    private final ReservationDashboardService reservationDashboardService;

    public OwnerDashboardController(ReservationDashboardService reservationDashboardService) {
        this.reservationDashboardService = reservationDashboardService;
    }

    @Operation(summary = "매장 메인 대시보드",
            description = "오늘 확정·남은 예약, 휴무 여부, 남은 빈 시간대와 이번 달(시술일 기준) 요약: "
                    + "확정 예약 금액, 고객·매장 사유 취소, 고객 취소율, 취소로 남은 예약금, 재판매 금액·회수율, 초임박 판매")
    @GetMapping
    public ResponseEntity<ApiResponse<ReservationDashboardResponse>> getDashboard(@LoginUser AuthUser loginUser) {
        return ResponseEntity.ok(ApiResponse.success(reservationDashboardService.getOwnerDashboard(loginUser)));
    }

    @Operation(summary = "매장 기간 통계",
            description = "from~to 시술일(둘 다 포함, 최대 92일). 생략 시 이번 달 1일~말일. "
                    + "기간 요약, 날짜별 추이, 시술별 성과")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<ReservationStatsResponse>> getStats(
            @LoginUser AuthUser loginUser,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(reservationDashboardService.getOwnerStats(loginUser, from, to)));
    }
}
