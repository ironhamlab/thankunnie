package com.ucamp.app.domain.cancel.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.LoginUser;
import com.ucamp.app.common.response.ApiResponse;
import com.ucamp.app.domain.cancel.dto.response.CancelQuoteResponse;
import com.ucamp.app.domain.cancel.service.CancelQuoteService;

/** 예약 취소·환불 API. */
@Tag(name = "Cancel", description = "예약 취소·환불")
@RestController
@RequestMapping("/api/v1/reservations")
public class CancelController {

    private final CancelQuoteService cancelQuoteService;

    public CancelController(CancelQuoteService cancelQuoteService) {
        this.cancelQuoteService = cancelQuoteService;
    }

    @Operation(summary = "취소 환불 견적 조회",
            description = "고객은 본인 예약, 사장님은 본인 매장 예약. 사장님 대리 취소 환불액 범위는 policyRefundAmount ~ paidAmount")
    @GetMapping("/{reservationId}/cancel-quote")
    public ApiResponse<CancelQuoteResponse> getCancelQuote(@LoginUser AuthUser user,
            @PathVariable Long reservationId) {
        return ApiResponse.success(cancelQuoteService.getQuote(user, reservationId));
    }
}
