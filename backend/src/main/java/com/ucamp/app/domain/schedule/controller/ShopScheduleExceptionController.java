package com.ucamp.app.domain.schedule.controller;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.LoginUser;
import com.ucamp.app.common.response.ApiResponse;
import com.ucamp.app.domain.schedule.dto.request.ScheduleExceptionSaveRequest;
import com.ucamp.app.domain.schedule.dto.response.ScheduleExceptionResponse;
import com.ucamp.app.domain.schedule.service.ShopScheduleExceptionService;

/**
 * 사장님 시간대 열기·닫기 API. 매장은 로그인한 사장님의 매장으로 정해진다.
 */
@Tag(name = "시간대 열기·닫기", description = "사장님 일회성 연장 영업·휴무 (예외 일정)")
@RestController
@RequestMapping("/api/v1/schedule-exceptions")
public class ShopScheduleExceptionController {

    private final ShopScheduleExceptionService shopScheduleExceptionService;

    public ShopScheduleExceptionController(ShopScheduleExceptionService shopScheduleExceptionService) {
        this.shopScheduleExceptionService = shopScheduleExceptionService;
    }

    @Operation(summary = "시간대 열기·닫기 등록", description = "30분 단위, 오늘 포함 60일 이내, 열기는 시작 30분 전까지")
    @PostMapping
    public ResponseEntity<ApiResponse<ScheduleExceptionResponse>> createException(
            @LoginUser AuthUser loginUser,
            @Valid @RequestBody ScheduleExceptionSaveRequest request) {
        ScheduleExceptionResponse response = shopScheduleExceptionService.createException(loginUser, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(summary = "시간대 열기·닫기 목록", description = "from~to 날짜와 겹치는 일정을 시작 시각 순으로 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ScheduleExceptionResponse>>> getExceptions(
            @LoginUser AuthUser loginUser,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(shopScheduleExceptionService.getExceptions(loginUser, from, to)));
    }

    @Operation(summary = "시간대 열기·닫기 수정")
    @PutMapping("/{exceptionId}")
    public ResponseEntity<ApiResponse<ScheduleExceptionResponse>> updateException(
            @LoginUser AuthUser loginUser,
            @PathVariable Long exceptionId,
            @Valid @RequestBody ScheduleExceptionSaveRequest request) {
        ScheduleExceptionResponse response =
                shopScheduleExceptionService.updateException(loginUser, exceptionId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "시간대 열기·닫기 삭제")
    @DeleteMapping("/{exceptionId}")
    public ResponseEntity<Void> deleteException(@LoginUser AuthUser loginUser, @PathVariable Long exceptionId) {
        shopScheduleExceptionService.deleteException(loginUser, exceptionId);
        return ResponseEntity.noContent().build();
    }
}
