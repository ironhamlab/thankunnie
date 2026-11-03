package com.ucamp.app.common.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 에러 코드 정의. 추가 시 docs/api/error-codes.md 에도 동일하게 기재한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // COMMON
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_400", "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON_401", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON_403", "접근 권한이 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_404", "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON_405", "지원하지 않는 HTTP 메서드입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "서버 내부 오류가 발생했습니다."),

    // AUTH
    AUTH_DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "AUTH_001", "이미 사용 중인 아이디입니다."),
    AUTH_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "AUTH_002", "아이디 또는 비밀번호가 올바르지 않습니다."),
    AUTH_INACTIVE_ACCOUNT(HttpStatus.FORBIDDEN, "AUTH_003", "탈퇴한 계정입니다. 같은 휴대폰 번호로 다시 가입할 수 있어요."),
    AUTH_PHONE_VERIFICATION_FAILED(HttpStatus.BAD_REQUEST, "AUTH_004", "휴대폰 인증을 확인해 주세요."),
    AUTH_PHONE_SEND_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "AUTH_005", "인증번호 요청 횟수를 초과했습니다."),
    AUTH_PHONE_SEND_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_006", "인증번호 발송에 실패했습니다."),
    AUTH_BUSINESS_INVALID(HttpStatus.BAD_REQUEST, "AUTH_007", "사업자 정보를 확인해 주세요."),
    AUTH_BUSINESS_LOOKUP_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_008", "국세청 사업자 정보를 조회하지 못했습니다. 잠시 후 다시 시도해 주세요."),
    AUTH_DUPLICATE_BUSINESS_NUMBER(HttpStatus.CONFLICT, "AUTH_009", "이미 등록된 사업자등록번호입니다."),
    AUTH_DUPLICATE_PHONE(HttpStatus.CONFLICT, "AUTH_010", "이미 가입된 휴대폰 번호입니다."),
    AUTH_SHOP_PENDING(HttpStatus.FORBIDDEN, "AUTH_011", "매장 가입 승인 대기 중입니다."),
    AUTH_SHOP_REJECTED(HttpStatus.FORBIDDEN, "AUTH_012", "매장 가입이 거절되었습니다."),

    // USER
    USER_WITHDRAW_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "USER_001", "비밀번호가 올바르지 않습니다."),
    USER_WITHDRAW_HAS_RESERVATION(HttpStatus.CONFLICT, "USER_002", "진행 중인 예약이 있어 탈퇴할 수 없습니다. 결제 대기 중이면 잠시 후, 예약이 있으면 취소하거나 방문을 마친 뒤 다시 시도해 주세요."),

    // ADMIN
    ADMIN_SHOP_APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "ADMIN_001", "매장 가입 신청을 찾을 수 없습니다."),
    ADMIN_SHOP_APPLICATION_NOT_PENDING(HttpStatus.CONFLICT, "ADMIN_002", "이미 처리된 가입 신청입니다."),
    ADMIN_SHOP_APPLICATION_NOT_APPROVED(HttpStatus.CONFLICT, "ADMIN_003", "승인된 매장이 아닙니다."),
    ADMIN_SHOP_APPLICATION_HAS_RESERVATION(HttpStatus.CONFLICT, "ADMIN_004", "진행 중이거나 앞으로 있을 예약이 있어 승인을 취소할 수 없습니다."),
    ADMIN_SHOP_APPLICATION_NOT_REJECTED(HttpStatus.CONFLICT, "ADMIN_005", "거절된 신청이 아닙니다."),

    // TERMS
    TERMS_NOT_FOUND(HttpStatus.NOT_FOUND, "TERMS_001", "약관을 찾을 수 없습니다."),
    TERMS_REQUIRED_NOT_AGREED(HttpStatus.BAD_REQUEST, "TERMS_002", "필수 약관에 모두 동의해야 합니다."),
    TERMS_SMS_AGREEMENT_REQUIRED(HttpStatus.CONFLICT, "TERMS_003", "알림 문자 수신 동의가 필요합니다."),
    TERMS_MODEL_PHOTO_AGREEMENT_REQUIRED(HttpStatus.CONFLICT, "TERMS_004", "초보 시술 촬영 동의가 필요합니다."),

    // RESERVATION
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "RESERVATION_001", "예약을 찾을 수 없습니다."),
    RESERVATION_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "RESERVATION_002", "이미 확정된 예약입니다."),
    RESERVATION_HOLD_EXPIRED(HttpStatus.CONFLICT, "RESERVATION_003", "결제 대기 시간이 만료되었습니다."),
    RESERVATION_NOT_HOLD(HttpStatus.CONFLICT, "RESERVATION_004", "결제 대기 중인 예약이 아닙니다."),
    RESERVATION_DEPOSIT_MISMATCH(HttpStatus.BAD_REQUEST, "RESERVATION_005", "결제 금액이 예약금과 다릅니다."),
    RESERVATION_HOLD_EXISTS(HttpStatus.CONFLICT, "RESERVATION_006", "결제 대기 중인 예약이 있습니다."),
    RESERVATION_QUEUE_NOT_YOUR_TURN(HttpStatus.CONFLICT, "RESERVATION_007", "아직 예약 순서가 아닙니다."),
    RESERVATION_NOT_CONFIRMED(HttpStatus.CONFLICT, "RESERVATION_008", "확정된 예약만 노쇼로 표시할 수 있습니다."),
    RESERVATION_NOT_ENDED(HttpStatus.CONFLICT, "RESERVATION_009", "시술이 끝난 뒤에 노쇼로 표시할 수 있습니다."),

    // NOTIFICATION
    SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION_001", "구독을 찾을 수 없습니다."),
    SUBSCRIPTION_INVALID_TARGET(HttpStatus.BAD_REQUEST, "NOTIFICATION_002", "구독 유형에 맞지 않는 구독 대상입니다."),
    SUBSCRIPTION_INVALID_TIME(HttpStatus.BAD_REQUEST, "NOTIFICATION_003", "알림 희망 시간이 올바르지 않습니다."),
    SUBSCRIPTION_DUPLICATE(HttpStatus.CONFLICT, "NOTIFICATION_004", "같은 대상과 희망 시간의 구독이 이미 있습니다."),
    SUBSCRIPTION_INVALID_TREATMENT(HttpStatus.BAD_REQUEST, "NOTIFICATION_005", "알림에 고를 수 있는 시술이 아닙니다."),
    INBOX_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION_006", "받은 알림을 찾을 수 없습니다."),

    // VACANCY
    VACANCY_BOOKING_CLOSED(HttpStatus.CONFLICT, "VACANCY_001", "예약 접수가 마감된 시간입니다."),
    VACANCY_PAYMENT_CLOSED(HttpStatus.CONFLICT, "VACANCY_002", "결제 마감 시간이 지났습니다."),
    VACANCY_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "VACANCY_003", "오늘부터 60일 이내만 예약할 수 있습니다."),
    VACANCY_INVALID_TIME_UNIT(HttpStatus.BAD_REQUEST, "VACANCY_004", "시각은 30분 단위여야 합니다."),
    VACANCY_INVALID_TIME_RANGE(HttpStatus.BAD_REQUEST, "VACANCY_005", "종료 시각은 시작 시각보다 뒤여야 합니다."),
    VACANCY_TIME_TAKEN(HttpStatus.CONFLICT, "VACANCY_006", "이미 예약된 시간입니다."),
    VACANCY_INVALID_SALE_PRICE(HttpStatus.BAD_REQUEST, "VACANCY_007", "판매가는 100원 단위이며 정가 이하여야 합니다."),
    VACANCY_INVALID_LAST_MINUTE_PRICE(HttpStatus.BAD_REQUEST, "VACANCY_008", "초임박 판매가는 일반 판매가 이하여야 합니다."),
    VACANCY_OPEN_CLOSED(HttpStatus.BAD_REQUEST, "VACANCY_009", "시간대 열기는 시작 30분 전까지 등록할 수 있습니다."),
    VACANCY_SCHEDULE_EXCEPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "VACANCY_010", "시간대 열기·닫기 일정을 찾을 수 없습니다."),
    VACANCY_RESERVED_TIME(HttpStatus.CONFLICT, "VACANCY_011", "예약이 있는 시간은 닫을 수 없습니다."),
    VACANCY_INVALID_BUSINESS_DAYS(HttpStatus.BAD_REQUEST, "VACANCY_012", "영업시간은 월요일부터 일요일까지 한 번씩 입력해야 합니다."),
    VACANCY_NOT_OPEN_TIME(HttpStatus.CONFLICT, "VACANCY_013", "영업하지 않는 시간입니다."),
    VACANCY_BUSINESS_TIME_REQUIRED(HttpStatus.BAD_REQUEST, "VACANCY_014", "영업일은 시작·종료 시각을 입력해야 합니다."),
    VACANCY_INVALID_DURATION(HttpStatus.BAD_REQUEST, "VACANCY_015", "시술 소요 시간이 올바르지 않습니다."),
    VACANCY_USER_TIME_OVERLAP(HttpStatus.CONFLICT, "VACANCY_016", "같은 시간에 진행 중인 예약이 있습니다."),
    VACANCY_SCHEDULE_EXCEPTION_OVERLAP(HttpStatus.CONFLICT, "VACANCY_017", "같은 시간에 이미 등록한 열기·닫기 일정이 있습니다."),

    // PAYMENT
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_001", "결제 정보를 찾을 수 없습니다."),
    PAYMENT_PORTONE_ERROR(HttpStatus.BAD_GATEWAY, "PAYMENT_002", "결제 대행사 연동 중 오류가 발생했습니다."),
    PAYMENT_NOT_REFUNDABLE(HttpStatus.CONFLICT, "PAYMENT_003", "환불할 수 없는 결제 상태입니다."),
    PAYMENT_INVALID_REFUND_AMOUNT(HttpStatus.BAD_REQUEST, "PAYMENT_004", "환불 금액이 올바르지 않습니다."),
    PAYMENT_REFUND_UNCERTAIN(HttpStatus.GATEWAY_TIMEOUT, "PAYMENT_005",
            "환불 결과를 확인하고 있습니다. 잠시 후 결제 상태를 확인해 주세요."),
    PAYMENT_NOT_PAID(HttpStatus.BAD_REQUEST, "PAYMENT_006", "결제가 완료되지 않았습니다."),
    PAYMENT_RESERVATION_MISMATCH(HttpStatus.BAD_REQUEST, "PAYMENT_007", "예약과 맞지 않는 결제입니다."),
    // TREATMENT
    TREATMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "TREATMENT_001", "시술을 찾을 수 없습니다."),

    // CANCEL
    CANCEL_RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "CANCEL_001", "취소할 예약을 찾을 수 없습니다."),
    CANCEL_NOT_CONFIRMED(HttpStatus.CONFLICT, "CANCEL_002", "확정된 예약만 취소할 수 있습니다."),
    CANCEL_ALREADY_REQUESTED(HttpStatus.CONFLICT, "CANCEL_003", "이미 취소 요청된 예약입니다."),
    CANCEL_ALREADY_STARTED(HttpStatus.CONFLICT, "CANCEL_004", "시술 시작 이후에는 취소할 수 없습니다."),
    CANCEL_GRACE_ALREADY_USED(HttpStatus.CONFLICT, "CANCEL_005", "오늘 무료 취소를 이미 사용했습니다."),
    CANCEL_INVALID_REFUND_AMOUNT(HttpStatus.BAD_REQUEST, "CANCEL_006", "환불액은 정책 환불액 이상, 납부 예약금 이하여야 합니다."),
    CANCEL_QUOTE_CHANGED(HttpStatus.CONFLICT, "CANCEL_007", "환불 금액이 변경되었습니다. 견적을 다시 확인해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
