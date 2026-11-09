# 예약 취소·환불 API

확정(`CONFIRMED`) 예약을 시술 시작 전에 취소하고 예약금을 환불한다.
고객은 본인 예약을 직접 취소하고, 매장은 본인 매장 예약을 매장 사유 또는 고객 요청으로 취소한다.
현재 사용자는 `@LoginUser AuthUser`를 사용한다. 모든 JSON 응답은 `ApiResponse<T>` 형식이고, 시각은 `+09:00` 오프셋의 ISO-8601 문자열이다.

| 메서드 | 경로 | 동작 | 권한 | 성공 |
|---|---|---|---|---|
| GET | `/api/v1/reservations/{reservationId}/cancel-quote` | 취소 환불 견적 | 고객·매장 | 200 |
| POST | `/api/v1/reservations/{reservationId}/cancel` | 고객 직접 취소 | 고객 | 200 |
| POST | `/api/v1/reservations/{reservationId}/owner-cancel` | 매장 취소 | 매장 | 200 |

- 고객은 본인 예약만, 매장은 본인 매장 예약만 다룬다. 다른 사람의 예약은 존재 여부를 드러내지 않도록 `CANCEL_001`(404)이다.
- 관리자는 취소하지 않는다(`COMMON_403`).
- 취소할 수 있는 예약은 확정 상태이고 시술 시작 전인 예약뿐이다.

## 환불 규칙

고객 직접 취소의 정책 환불액은 시술 시작일과 취소일의 **한국 날짜 차이**로 정한다. 시각과 관계없이 자정이 경계다.

| 시작일까지 | 환불 비율 |
|---|---|
| 2일 이상 전 | 100% |
| 하루 전 | 50% |
| 당일 | 0% |

- 정책 환불액 = 납부 예약금 × 비율. 원 미만은 버린다.
- **무료 취소**: 결제 승인 후 10분 안에 취소하면 정책과 관계없이 전액 환불한다. 하루(한국 날짜)에 1번만 쓸 수 있다.
  점유할 때 시작까지 3시간 이하였던 초임박 예약은 대상이 아니다.
- 매장 사유 취소(`SHOP_FAULT`)는 납부 예약금 전액을 환불한다.
- 고객 요청 대리 취소(`CUSTOMER_REQUEST`)는 매장이 정책 환불액 이상, 납부 예약금 이하에서 환불액을 정한다. 이때 정책 환불액에는 무료 취소를 반영하지 않는다.

## 취소 환불 견적 `GET /api/v1/reservations/{reservationId}/cancel-quote`

취소 버튼을 누르기 전에 환불 예정 금액을 보여 줄 때 부른다. 아무것도 저장하지 않는다.

응답 `data`:

```json
{
  "reservationId": 1001,
  "paidAmount": 20000,
  "refundRate": 50,
  "policyRefundAmount": 10000,
  "refundAmount": 20000,
  "graceCancelResult": "APPLIED",
  "graceApplied": true,
  "remainingGraceCount": 1,
  "quotedAt": "2026-11-05T14:03:00+09:00"
}
```

| 필드 | 설명 |
|---|---|
| `paidAmount` | 납부 예약금 |
| `refundRate` | 날짜 기준 환불 비율(%) |
| `policyRefundAmount` | 정책 환불액. 매장 대리 취소 환불액의 하한 |
| `refundAmount` | 고객 직접 취소 시 실제 환불액. 무료 취소면 `paidAmount` |
| `graceCancelResult` | 무료 취소 판정. 아래 표 |
| `graceApplied` | 무료 취소를 적용했는지 (`graceCancelResult = APPLIED`) |
| `remainingGraceCount` | 오늘 남은 무료 취소 횟수 (0 또는 1) |
| `quotedAt` | 견적을 계산한 서버 시각 |

| `graceCancelResult` | 뜻 |
|---|---|
| `APPLIED` | 무료 취소를 적용해 전액 환불. 취소하면 오늘 횟수를 쓴다 |
| `NOT_NEEDED` | 정책으로도 전액 환불이라 적용하지 않음. 횟수를 쓰지 않는다 |
| `LAST_MINUTE_HOLD` | 점유할 때 시작까지 3시간 이하였던 초임박 예약이라 대상 아님 |
| `WINDOW_PASSED` | 결제 후 10분이 지남 |
| `ALREADY_USED_TODAY` | 오늘 무료 취소를 이미 씀 |

## 고객 직접 취소 `POST /api/v1/reservations/{reservationId}/cancel`

```json
{ "expectedRefundAmount": 20000 }
```

| 필드 | 필수 | 설명 |
|---|---|---|
| `expectedRefundAmount` | O | 고객이 본 견적의 `refundAmount`. 0 이상 |

- 서버가 환불액을 다시 계산해 `expectedRefundAmount`와 다르면 `CANCEL_007`(409)이다. 화면은 견적을 다시 조회해 고객에게 다시 확인받는다.
- 무료 취소를 적용하는데 그 사이 오늘 횟수를 이미 썼으면 `CANCEL_005`(409)이다.

응답 `data`:

```json
{
  "cancelId": 31,
  "reservationId": 1001,
  "cancelType": "CUSTOMER",
  "cancelStatus": "COMPLETED",
  "reservationStatus": "CANCELLED",
  "refundAmount": 20000,
  "graceApplied": true,
  "requestedAt": "2026-11-05T14:03:10+09:00",
  "completedAt": "2026-11-05T14:03:11+09:00"
}
```

## 매장 취소 `POST /api/v1/reservations/{reservationId}/owner-cancel`

```json
{
  "cancelType": "CUSTOMER_REQUEST",
  "refundAmount": 15000,
  "closeAfterCancel": true,
  "reason": "고객 요청으로 취소"
}
```

| 필드 | 필수 | 설명 |
|---|---|---|
| `cancelType` | O | `SHOP_FAULT`(매장 사유) 또는 `CUSTOMER_REQUEST`(고객 요청 대리). `CUSTOMER`는 `COMMON_403` |
| `refundAmount` | `CUSTOMER_REQUEST`만 | 견적의 `policyRefundAmount` 이상 `paidAmount` 이하. 없으면 `COMMON_400`, 범위 밖이면 `CANCEL_006`. `SHOP_FAULT`는 보내도 무시하고 전액 환불 |
| `closeAfterCancel` | O | `true`면 취소한 예약 시간을 시간대 닫기로 막아 다시 판매하지 않는다 |
| `reason` | X | 500자 이하. 결제 취소 사유 문구 뒤에 붙고, 시간을 닫으면 닫기 사유로 저장한다 |

응답 `data`: 고객 직접 취소 응답에서 `graceApplied`를 빼고 아래를 더한다.

| 필드 | 설명 |
|---|---|
| `closed` | 취소한 시간을 닫았는지 |
| `customerName`, `customerPhone` | 예약 고객. 매장이 고객에게 연락할 때 쓴다 |

## 결제 취소 결과 처리

취소를 접수하면 예약은 `CANCEL_PENDING`이 되고 결제 취소(포트원)를 부른다. 고객·매장 취소 모두 같다.

| 결과 | 응답 | 예약 | `cancelStatus` |
|---|---|---|---|
| 성공 | 200 | `CANCELLED` | `COMPLETED` |
| 환불액 0원 | 200 (결제 취소를 부르지 않음) | `CANCELLED` | `COMPLETED` |
| 확실한 실패 | 결제 오류(`PAYMENT_00x`) 그대로 | `CONFIRMED`로 되돌림 | `FAILED` |
| 결과 불명 (`PAYMENT_005`, 예상 못 한 오류) | 200 | `CANCEL_PENDING` 유지 | `UNKNOWN` |

- 결과 불명이면 실제로 환불됐을 수 있어 시간을 계속 막아 둔다. 화면은 "환불을 확인하고 있어요" 안내를 보여준다.
- 실패한 취소는 같은 취소 행으로 다시 시도한다.

## 오류

| 코드 | 상태 | 조건 |
|---|---|---|
| COMMON_400 | 400 | 입력 검증 실패, 대리 취소 환불액 누락 |
| COMMON_401 | 401 | 비로그인 |
| COMMON_403 | 403 | 관리자, 역할에 맞지 않는 `cancelType`, 매장이 고객 취소 API 호출 등 |
| CANCEL_001 | 404 | 없는 예약, 다른 고객·다른 매장의 예약 |
| CANCEL_002 | 409 | 확정 예약이 아님 (점유·만료) |
| CANCEL_003 | 409 | 이미 취소 처리 중이거나 취소 완료 |
| CANCEL_004 | 409 | 시술 시작 이후 |
| CANCEL_005 | 409 | 오늘 무료 취소를 이미 사용 |
| CANCEL_006 | 400 | 대리 취소 환불액이 정책 환불액 미만이거나 납부액 초과 |
| CANCEL_007 | 409 | 고객이 본 환불액과 서버 재계산 금액이 다름 |
| PAYMENT_001 | 404 | 확정 예약인데 결제 기록이 없음 (데이터 오류) |
| PAYMENT_00x | - | 결제 취소 실패. `error-codes.md`의 PAYMENT 참고 |
