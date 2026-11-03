package com.ucamp.app.domain.cancel.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.config.SecurityConfig;
import com.ucamp.app.domain.cancel.dto.response.CancelQuoteResponse;
import com.ucamp.app.domain.cancel.dto.response.CancelResultResponse;
import com.ucamp.app.domain.cancel.dto.response.OwnerCancelResponse;
import com.ucamp.app.domain.cancel.entity.CancelStatus;
import com.ucamp.app.domain.cancel.entity.CancelType;
import com.ucamp.app.domain.cancel.policy.GraceCancelResult;
import com.ucamp.app.domain.cancel.service.CancelQuoteService;
import com.ucamp.app.domain.cancel.service.CancelService;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;

@WebMvcTest(CancelController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class CancelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CancelQuoteService cancelQuoteService;

    @MockitoBean
    private CancelService cancelService;

    @Test
    @DisplayName("비로그인 견적 조회는 인증을 요구한다")
    void getCancelQuote_anonymous_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/reservations/100/cancel-quote")).andExpect(status().isUnauthorized());
        verifyNoInteractions(cancelQuoteService);
    }

    @Test
    @DisplayName("견적을 공통 응답 형식으로 반환한다")
    void getCancelQuote_returnsQuote() throws Exception {
        given(cancelQuoteService.getQuote(any(), eq(100L))).willReturn(new CancelQuoteResponse(100L, 20_000, 50,
                10_000, 20_000, GraceCancelResult.APPLIED, true, 1,
                OffsetDateTime.parse("2026-11-01T12:00:00+09:00")));

        mockMvc.perform(get("/api/v1/reservations/100/cancel-quote").header("X-Test-User", "CUSTOMER_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reservationId").value(100))
                .andExpect(jsonPath("$.data.policyRefundAmount").value(10_000))
                .andExpect(jsonPath("$.data.refundAmount").value(20_000))
                .andExpect(jsonPath("$.data.graceCancelResult").value("APPLIED"))
                .andExpect(jsonPath("$.data.graceApplied").value(true))
                .andExpect(jsonPath("$.data.quotedAt").value("2026-11-01T12:00:00+09:00"));
    }

    @Test
    @DisplayName("업무 예외는 에러 코드로 응답한다")
    void getCancelQuote_businessError() throws Exception {
        given(cancelQuoteService.getQuote(any(), eq(100L)))
                .willThrow(new BusinessException(ErrorCode.CANCEL_RESERVATION_NOT_FOUND));

        mockMvc.perform(get("/api/v1/reservations/100/cancel-quote").header("X-Test-User", "CUSTOMER_1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CANCEL_001"));
    }

    @Test
    @DisplayName("비로그인 취소는 인증을 요구한다")
    void cancel_anonymous_unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/reservations/100/cancel")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedRefundAmount\":10000}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(cancelService);
    }

    @Test
    @DisplayName("화면 견적 환불액이 없거나 음수면 거부한다")
    void cancel_invalidBody_badRequest() throws Exception {
        for (String body : new String[] {"{}", "{\"expectedRefundAmount\":-1}"}) {
            mockMvc.perform(post("/api/v1/reservations/100/cancel").header("X-Test-User", "CUSTOMER_1")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(cancelService);
    }

    @Test
    @DisplayName("취소 결과를 공통 응답 형식으로 반환한다")
    void cancel_returnsResult() throws Exception {
        given(cancelService.cancelByCustomer(any(), eq(100L), any())).willReturn(new CancelResultResponse(7L, 100L,
                CancelType.CUSTOMER, CancelStatus.COMPLETED, ReservationStatus.CANCELLED, 10_000, false,
                OffsetDateTime.parse("2026-11-01T12:00:00+09:00"), OffsetDateTime.parse("2026-11-01T12:00:03+09:00")));

        mockMvc.perform(post("/api/v1/reservations/100/cancel").header("X-Test-User", "CUSTOMER_1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedRefundAmount\":10000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cancelId").value(7))
                .andExpect(jsonPath("$.data.cancelStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.reservationStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.data.refundAmount").value(10_000));
    }

    @Test
    @DisplayName("매장 취소는 유형과 닫기 여부가 없으면 거부한다")
    void cancelByOwner_missingFields_badRequest() throws Exception {
        for (String body : new String[] {"{\"closeAfterCancel\":false}", "{\"cancelType\":\"SHOP_FAULT\"}"}) {
            mockMvc.perform(post("/api/v1/reservations/100/owner-cancel").header("X-Test-User", "OWNER_1")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(cancelService);
    }

    @Test
    @DisplayName("매장 취소 결과에 닫기 여부와 고객 연락처를 담아 반환한다")
    void cancelByOwner_returnsResultWithContact() throws Exception {
        given(cancelService.cancelByOwner(any(), eq(100L), any())).willReturn(new OwnerCancelResponse(7L, 100L,
                CancelType.SHOP_FAULT, CancelStatus.COMPLETED, ReservationStatus.CANCELLED, 20_000, true, "고객",
                "010-1234-5678", OffsetDateTime.parse("2026-11-01T12:00:00+09:00"),
                OffsetDateTime.parse("2026-11-01T12:00:03+09:00")));

        mockMvc.perform(post("/api/v1/reservations/100/owner-cancel").header("X-Test-User", "OWNER_1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancelType\":\"SHOP_FAULT\",\"closeAfterCancel\":true,\"reason\":\"원장님 부상\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cancelType").value("SHOP_FAULT"))
                .andExpect(jsonPath("$.data.closed").value(true))
                .andExpect(jsonPath("$.data.customerPhone").value("010-1234-5678"));
    }
}
