package com.ucamp.app.domain.vacancy.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.ucamp.app.common.policy.BookingPolicy;
import com.ucamp.app.common.response.ApiResponse;
import com.ucamp.app.domain.vacancy.dto.response.AvailableTimesResponse;
import com.ucamp.app.domain.vacancy.service.AvailableTimeQueryService;

/**
 * 가용 시간 조회 API (B-06). 비로그인 조회 가능.
 */
@Tag(name = "가용 시간", description = "영업시간·시간대 열기·닫기·예약을 반영한 날짜별 시작 가능 시각")
@RestController
@RequestMapping("/api/v1/available-times")
public class AvailableTimeController {

    private final AvailableTimeQueryService availableTimeQueryService;

    public AvailableTimeController(AvailableTimeQueryService availableTimeQueryService) {
        this.availableTimeQueryService = availableTimeQueryService;
    }

    @Operation(summary = "날짜별 시작 가능 시각",
            description = "30분 단위. durationMinutes 만큼 연속으로 비어 있는 시각만 반환한다. 생략하면 30분."
                    + " 시작 20분 전이 지난 시각은 빠진다. 날짜는 오늘부터 60일 이내."
                    + " extendedStartTimes 는 그중 영업시간 밖 연장 영업(매장이 연 시간) 덕분에 가능한 시각이다")
    @GetMapping
    public ResponseEntity<ApiResponse<AvailableTimesResponse>> getAvailableTimes(
            @RequestParam Long shopId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "" + BookingPolicy.TIME_UNIT_MINUTES) int durationMinutes) {
        return ResponseEntity.ok(ApiResponse.success(
                availableTimeQueryService.getAvailableTimes(shopId, date, durationMinutes)));
    }
}
