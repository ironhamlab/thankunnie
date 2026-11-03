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
 * 매장 대시보드 API. 매장은 로그인한 매장 계정으로 정해진다.
 */
@Tag(name = "매장 대시보드", description = "오늘 운영 현황과 매출·예약 분석")
@RestController
@RequestMapping("/api/v1/owner/dashboard")
public class OwnerDashboardController {

    private final ReservationDashboardService reservationDashboardService;

    public OwnerDashboardController(ReservationDashboardService reservationDashboardService) {
        this.reservationDashboardService = reservationDashboardService;
    }

    @Operation(summary = "매장 홈 대시보드",
            description = "오늘 확정·남은 예약, 휴무 여부, 남은 빈 시간대, 내일 확정 예약, "
                    + "이번 달 1일~오늘 이용 금액과 지난달 같은 기간 비교, 찜 고객 수")
    @GetMapping
    public ResponseEntity<ApiResponse<ReservationDashboardResponse>> getDashboard(@LoginUser AuthUser loginUser) {
        return ResponseEntity.ok(ApiResponse.success(reservationDashboardService.getOwnerDashboard(loginUser)));
    }

    @Operation(summary = "매장 매출·예약 분석",
            description = "from~to 시술일(둘 다 포함, 최대 3660일). 생략 시 이번 달 1일~말일, all=true 면 서비스 이용 시작일~오늘. "
                    + "62일 이하는 날짜별, 넘으면 월별 추이. 기간 요약과 직전 같은 길이 기간 요약, 요일별·시간대별·시술별 지표")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<ReservationStatsResponse>> getStats(
            @LoginUser AuthUser loginUser,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean all) {
        return ResponseEntity.ok(ApiResponse.success(
                reservationDashboardService.getOwnerStats(loginUser, from, to, all)));
    }
}
