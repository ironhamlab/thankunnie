package com.ucamp.app.domain.cancel.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.LoginUser;
import com.ucamp.app.common.response.ApiResponse;
import com.ucamp.app.domain.cancel.dto.request.CustomerCancelRequest;
import com.ucamp.app.domain.cancel.dto.request.OwnerCancelRequest;
import com.ucamp.app.domain.cancel.dto.response.CancelQuoteResponse;
import com.ucamp.app.domain.cancel.dto.response.CancelResultResponse;
import com.ucamp.app.domain.cancel.dto.response.OwnerCancelResponse;
import com.ucamp.app.domain.cancel.service.CancelQuoteService;
import com.ucamp.app.domain.cancel.service.CancelService;

/** 예약 취소·환불 API. */
@Tag(name = "Cancel", description = "예약 취소·환불")
@RestController
@RequestMapping("/api/v1/reservations")
public class CancelController {

    private final CancelQuoteService cancelQuoteService;
    private final CancelService cancelService;

    public CancelController(CancelQuoteService cancelQuoteService, CancelService cancelService) {
        this.cancelQuoteService = cancelQuoteService;
        this.cancelService = cancelService;
    }

    @Operation(summary = "취소 환불 견적 조회",
            description = "고객은 본인 예약, 매장은 본인 매장 예약. 매장 대리 취소 환불액 범위는 policyRefundAmount ~ paidAmount")
    @GetMapping("/{reservationId}/cancel-quote")
    public ApiResponse<CancelQuoteResponse> getCancelQuote(@LoginUser AuthUser user,
            @PathVariable Long reservationId) {
        return ApiResponse.success(cancelQuoteService.getQuote(user, reservationId));
    }

    @Operation(summary = "고객 직접 취소",
            description = "expectedRefundAmount 는 견적의 refundAmount. 재계산 금액이 다르면 CANCEL_007 로 응답하므로 견적을 다시 조회해 재확인한다. "
                    + "결제 취소가 확실히 실패하면 PAYMENT_00x 로 응답하고 예약은 확정 상태로 돌아간다. "
                    + "결과 불명이면 cancelStatus=UNKNOWN 으로 응답하고 예약은 취소 처리 중으로 남는다")
    @PostMapping("/{reservationId}/cancel")
    public ApiResponse<CancelResultResponse> cancelByCustomer(@LoginUser AuthUser user,
            @PathVariable Long reservationId, @Valid @RequestBody CustomerCancelRequest request) {
        return ApiResponse.success(cancelService.cancelByCustomer(user, reservationId, request));
    }

    @Operation(summary = "매장 취소",
            description = "SHOP_FAULT 는 납부액 전액 환불, CUSTOMER_REQUEST 는 refundAmount(견적의 policyRefundAmount ~ paidAmount) 환불. "
                    + "closeAfterCancel=true 면 취소한 시간을 닫는다. 결제 취소 결과 처리는 고객 직접 취소와 같다")
    @PostMapping("/{reservationId}/owner-cancel")
    public ApiResponse<OwnerCancelResponse> cancelByOwner(@LoginUser AuthUser user,
            @PathVariable Long reservationId, @Valid @RequestBody OwnerCancelRequest request) {
        return ApiResponse.success(cancelService.cancelByOwner(user, reservationId, request));
    }
}
