package com.ucamp.app.domain.reservation.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.ucamp.app.common.converter.BooleanToYnConverter;
import com.ucamp.app.common.entity.BaseTimeEntity;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;

/**
 * 예약. 고객이 예약 클릭(점유)할 때 HOLD 로 생성되고, 적용 가격·예약금은 클릭 시점 값으로 고정된다.
 * 회원·시술은 해당 엔티티가 생기기 전까지 ID 로만 참조한다.
 * 상태 변경(확정·취소·만료) 메서드는 예약 담당(D)이 추가한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "RESERVATION")
public class Reservation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_RESERVATION_GENERATOR")
    @SequenceGenerator(name = "SEQ_RESERVATION_GENERATOR", sequenceName = "SEQ_RESERVATION", allocationSize = 1)
    @Column(name = "RESERVATION_ID")
    private Long id;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Column(name = "TREATMENT_ID", nullable = false)
    private Long treatmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "STARTS_AT", nullable = false)
    private LocalDateTime startsAt;

    /** 종료 시각은 구간에 포함하지 않는다 */
    @Column(name = "ENDS_AT", nullable = false)
    private LocalDateTime endsAt;

    /** 클릭 시점 적용 판매가 */
    @Column(name = "APPLIED_PRICE", nullable = false)
    private Integer appliedPrice;

    /** 예약금 = min(적용 판매가, 20,000) */
    @Column(name = "DEPOSIT", nullable = false)
    private Integer deposit;

    /** 점유 만료 = 클릭 + 5분 */
    @Column(name = "HOLD_EXPIRES_AT", nullable = false)
    private LocalDateTime holdExpiresAt;

    @Column(name = "CONFIRMED_AT")
    private LocalDateTime confirmedAt;

    /** 고객 요청사항 최대 길이 (RESERVATION.CUSTOMER_REQUEST VARCHAR2(1000)) */
    public static final int CUSTOMER_REQUEST_MAX_LENGTH = 1000;

    @Column(name = "CUSTOMER_REQUEST", length = CUSTOMER_REQUEST_MAX_LENGTH)
    private String customerRequest;

    /**
     * 노쇼 여부. 매장이 종료 시각 이후 확정 예약에만 표시·해제한다(ReservationRepository.changeNoShow).
     * 상태는 CONFIRMED 그대로 두고, 이용 횟수·이용 추정 금액·피크 집계에서만 뺀다.
     */
    @Convert(converter = BooleanToYnConverter.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "NO_SHOW", nullable = false, length = 1)
    private boolean noShow;

    @Builder
    private Reservation(Long userId, Long treatmentId, LocalDateTime startsAt, LocalDateTime endsAt,
            Integer appliedPrice, Integer deposit, LocalDateTime holdExpiresAt) {
        this.userId = userId;
        this.treatmentId = treatmentId;
        this.status = ReservationStatus.HOLD;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.appliedPrice = appliedPrice;
        this.deposit = deposit;
        this.holdExpiresAt = holdExpiresAt;
        this.noShow = false;
    }

    /**
     * 결제 확인 뒤 점유를 예약 완료로 바꾼다. 점유 중(HOLD, 만료 전)이고 결제 금액이 예약금과 같을 때만 확정한다.
     * 동시에 확정·만료되지 않도록 예약 행을 잠근 뒤 호출한다.
     *
     * @param paidAmount 결제사에서 확인한 실제 결제 금액
     * @param now             확정 시각 (예약 행 잠금 뒤 읽은 현재 시각)
     * @param customerRequest 결제 화면에서 입력한 고객 요청사항. 앞뒤 공백을 제거해 저장하고,
     *                        null 이거나 공백뿐이면 저장하지 않는다(null)
     * @throws BusinessException validateConfirmable 과 같은 조건이거나 요청사항이 최대 길이를 넘으면 INVALID_INPUT.
     *                           예외가 나면 상태와 요청사항은 바뀌지 않는다
     */
    public void confirm(int paidAmount, LocalDateTime now, String customerRequest) {
        validateConfirmable(paidAmount, now);
        String request = customerRequest == null ? null : customerRequest.strip();
        if (request != null && request.length() > CUSTOMER_REQUEST_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "요청사항은 " + CUSTOMER_REQUEST_MAX_LENGTH + "자 이하로 입력해 주세요.");
        }
        this.status = ReservationStatus.CONFIRMED;
        this.confirmedAt = now;
        this.customerRequest = request == null || request.isEmpty() ? null : request;
    }

    /**
     * 고객이 결제 화면을 떠나거나 다른 시간으로 바꿀 때 점유를 푼다. 행을 지우지 않고 EXPIRED 로 바꾼다.
     * 아직 만료 전이면 만료 시각도 지금으로 당겨, 만료 처리 전에도 시간을 잡고 있지 않게 한다.
     * 동시에 확정되지 않도록 예약 행을 잠근 뒤 호출한다.
     *
     * @param now 푸는 시각 (예약 행 잠금 뒤 읽은 현재 시각)
     * @throws BusinessException 이미 확정이면 RESERVATION_ALREADY_CONFIRMED,
     *                           취소 처리 중·취소 완료면 RESERVATION_NOT_HOLD. 이미 EXPIRED 면 아무것도 하지 않는다
     */
    public void release(LocalDateTime now) {
        if (status == ReservationStatus.EXPIRED) {
            return;
        }
        if (status == ReservationStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.RESERVATION_ALREADY_CONFIRMED);
        }
        if (status != ReservationStatus.HOLD) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_HOLD);
        }
        this.status = ReservationStatus.EXPIRED;
        if (now.isBefore(holdExpiresAt)) {
            this.holdExpiresAt = now;
        }
    }

    /**
     * 같은 시술·같은 시작 시각의 점유인지 확인한다. 같은 시간을 다시 누르면 새로 만들지 않고 기존 점유를 돌려주는 데 쓴다.
     */
    public boolean isSameSlot(Long treatmentId, LocalDateTime startsAt) {
        return this.treatmentId.equals(treatmentId) && this.startsAt.equals(startsAt);
    }

    /**
     * 확정할 수 있는지 상태를 바꾸지 않고 검사한다.
     * 다른 검사(결제 마감 등)를 함께 마친 뒤 confirm 을 부르려는 쪽에서 먼저 호출한다.
     *
     * @param paidAmount 결제사에서 확인한 실제 결제 금액
     * @param now        판단 기준 시각
     * @throws BusinessException 이미 확정이면 RESERVATION_ALREADY_CONFIRMED,
     *                           EXPIRED 이거나 HOLD 라도 점유 만료 시각이 지났으면 RESERVATION_HOLD_EXPIRED,
     *                           취소 처리 중·취소 완료면 RESERVATION_NOT_HOLD,
     *                           결제 금액이 예약금과 다르면 RESERVATION_DEPOSIT_MISMATCH
     */
    public void validateConfirmable(int paidAmount, LocalDateTime now) {
        if (status == ReservationStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.RESERVATION_ALREADY_CONFIRMED);
        }
        // 만료 처리가 아직 돌지 않아 HOLD 로 남아 있어도 만료 시각이 지났으면 만료로 본다
        if (status == ReservationStatus.EXPIRED
                || status == ReservationStatus.HOLD && !now.isBefore(holdExpiresAt)) {
            throw new BusinessException(ErrorCode.RESERVATION_HOLD_EXPIRED);
        }
        if (status != ReservationStatus.HOLD) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_HOLD);
        }
        if (paidAmount != deposit) {
            throw new BusinessException(ErrorCode.RESERVATION_DEPOSIT_MISMATCH);
        }
    }
}
