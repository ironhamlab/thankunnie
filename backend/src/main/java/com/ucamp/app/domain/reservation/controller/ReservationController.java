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
import com.ucamp.app.domain.reservation.service.ReservationService;

/**
 * 예약 API.
 */
@Tag(name = "Reservation", description = "예약")
@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Operation(summary = "예약 생성(점유)",
            description = "고객이 예약을 클릭하면 5분간 점유(HOLD)하는 예약을 만든다. 적용 판매가와 예약금은 이 시점 기준으로 고정한다")
    @PostMapping
    public ResponseEntity<ApiResponse<ReservationCreateResponse>> createReservation(
            @LoginUser AuthUser loginUser, @Valid @RequestBody ReservationCreateRequest request) {
        ReservationCreateResponse response = reservationService.createReservation(loginUser, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }
}
