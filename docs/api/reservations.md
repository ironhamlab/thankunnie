# 예약 (D 예약 생성·조회 / UNNIE-92 예약 → 결제 화면 연결)

고객이 시술과 시작 시각을 고르면 5분간 시간을 점유(HOLD)하는 예약을 만들고,
예약금 결제가 확인되면 예약 완료(CONFIRMED)로 바꾼다.
현재 사용자는 B-01의 `@LoginUser AuthUser`를 사용한다. 모든 JSON 응답은 `ApiResponse<T>` 형식이다.
시각은 모두 한국 시간 `+09:00` 오프셋의 ISO-8601 문자열이다.

## API

| 메서드 | 경로 | 동작 | 권한 | 성공 |
|---|---|---|---|---|
| POST | `/api/v1/reservations` | 예약 생성(점유) | 고객 | 201 |
| GET | `/api/v1/my/reservations` | 내 예약 목록 | 고객 | 200 |
| GET | `/api/v1/my/reservations/{reservationId}` | 내 예약 상세 | 고객 | 200 |
| GET | `/api/v1/owner/reservations` | 본인 매장 예약 목록 | 사장님 | 200 |
| GET | `/api/v1/owner/reservations/{reservationId}` | 본인 매장 예약 상세 | 사장님 | 200 |

## 예약 생성(점유) `POST /api/v1/reservations`

고객이 예약하기 버튼을 누를 때 호출한다. 적용 판매가와 예약금은 이 시점 기준으로 고정한다.

요청:

```json
{
  "treatmentId": 1,
  "startsAt": "2026-10-29T15:00:00+09:00"
}
```

| 필드 | 필수 | 설명 |
|---|---|---|
| `treatmentId` | O | 판매 중(ACTIVE)인 시술 ID |
| `startsAt` | O | 시작 일시. 30분 단위, 오늘부터 60일 이내, 시작 20분 전까지 |

고객 요청사항은 이 요청에서 받지 않는다. 결제 화면에서 받아 예약 확정 때 저장한다 ([요청사항 저장](#요청사항-저장-예약-쪽-구현-완료-결제-연결-대기) 참고).

응답 `data` (201):

```json
{
  "reservationId": 1001,
  "treatmentId": 1,
  "status": "HOLD",
  "startsAt": "2026-10-29T15:00:00+09:00",
  "endsAt": "2026-10-29T16:00:00+09:00",
  "appliedPrice": 30000,
  "deposit": 20000,
  "holdExpiresAt": "2026-10-28T16:16:15.333+09:00"
}
```

- `endsAt`은 시작 시각 + 시술 소요 시간이며, 종료 시각 자체는 포함하지 않는다.
- `appliedPrice`는 일반·초임박 구간에 따라 계산한 판매가, `deposit`은 적용 판매가와 20,000원 중 낮은 금액이다.
- `holdExpiresAt`은 생성 시각 + 5분이다. 이 시각까지 결제하지 않으면 예약은 EXPIRED가 되고 시간이 풀린다.
- 같은 고객이 여러 매장에 동시에 누르거나 두 고객이 같은 시간을 동시에 눌러도 한 건만 점유되도록
  회원 → 매장 순으로 잠근 뒤 가용 시간과 본인 예약 겹침을 다시 검사한다.

## 예약 목록 `GET /api/v1/my/reservations`, `GET /api/v1/owner/reservations`

| 파라미터 | 기본값 | 설명 |
|---|---|---|
| `from`, `to` | 없음 | 시작 날짜(YYYY-MM-DD) 범위, 양 끝 포함 |
| `status` | 없음 | 여러 개는 `status=HOLD&status=CONFIRMED`처럼 반복해서 보낸다. 생략하면 EXPIRED를 뺀 전체 |
| `page`, `size` | 0, 20 | `page`는 0 이상, `size`는 1~100 |

- 정렬은 시작 시각 순이다.
- 프론트 `reservationApi.js`의 `toParams`가 `status` 반복 형식으로 만든다. axios 기본 배열 형식(`status[]=`)은 쓰지 않는다.

응답 `data`: `PageResponse<ReservationSummaryResponse>`
`content`, `page`, `size`, `totalElements`, `totalPages`, `hasNext`.

## 예약 상세 `GET /api/v1/my/reservations/{id}`, `GET /api/v1/owner/reservations/{id}`

- 고객은 본인 예약만, 사장님은 본인 매장 예약만 조회한다. 다른 사람의 예약은 존재 여부를 드러내지 않도록 404다.
- HOLD 예약도 조회된다. 결제 화면(`/checkout`)이 이 API로 점유 중인 예약을 불러온다.

응답 `data`:

```json
{
  "summary": {
    "reservationId": 1001,
    "status": "HOLD",
    "startsAt": "2026-10-29T15:00:00+09:00",
    "endsAt": "2026-10-29T16:00:00+09:00",
    "shopId": 1,
    "shopName": "언니네 네일",
    "treatmentId": 1,
    "treatmentName": "젤네일 원컬러",
    "customerName": "고객일",
    "appliedPrice": 30000,
    "deposit": 20000,
    "balance": 10000,
    "paymentStatus": null,
    "cancelStatus": null,
    "cancellable": false
  },
  "customerPhone": "010-1111-1111",
  "customerRequest": null,
  "holdExpiresAt": "2026-10-28T16:16:15.333+09:00",
  "confirmedAt": null,
  "reservedAt": "2026-10-28T16:11:15+09:00",
  "payment": null,
  "cancel": null
}
```

| 필드 | 설명 |
|---|---|
| `summary` | 목록과 같은 요약. `balance`는 현장 결제 잔금(적용 판매가 − 예약금) |
| `summary.cancellable` | 취소 버튼 표시 여부. 확정 상태이고 시작 전이면 true. 실제 취소 가능 판단은 취소 API가 한다 |
| `payment` | 최근 예약금 결제. `status`(READY, PAID, FAILED, UNKNOWN, REFUND_PENDING, PART_REFUNDED, REFUNDED), `amount`, `refundedAmount`, `approvedAt`, `refundedAt`. 결제 전이면 null |
| `cancel` | 취소. `cancelType`(CUSTOMER, SHOP_FAULT, CUSTOMER_REQUEST), `status`, `policyRefundAmount`, `refundAmount`, `requestedAt`, `completedAt`. 취소 전이면 null |

## 예약 상태

| 상태 | 뜻 |
|---|---|
| HOLD | 점유. 예약 클릭 후 결제 대기 (5분) |
| CONFIRMED | 예약 완료. 결제 확정 |
| CANCEL_PENDING | 취소 처리 중. 환불 완료 전까지 시간을 계속 잡고 있다 |
| CANCELLED | 취소 완료 |
| EXPIRED | 점유 만료. 5분 안에 결제하지 않음 |

## 요청사항 저장 (예약 쪽 구현 완료, 결제 연결 대기)

원장님께 남길 요청사항은 예약 화면(`/reserve`)이 아니라 결제 화면(`/checkout`)에서 입력받는다.
예약을 만든 뒤 요청사항만 고치는 API는 두지 않고, **결제 확인 요청에 함께 보내 결제가 확인될 때 저장**한다.

- 대상 API: `POST /api/v1/payments/verify` (결제 담당)
- 요청에 `customerRequest`(선택, 최대 1,000자)를 추가한다.

  ```json
  {
    "impUid": "imp_123456789012",
    "customerRequest": "손톱은 라운드로 부탁드려요"
  }
  ```

- 결제 확인 후 예약을 확정하는 같은 트랜잭션에서 `RESERVATION.CUSTOMER_REQUEST`에 저장한다.
  결제가 실패하면 저장하지 않는다.
- **예약 쪽 구현 (완료)**: `ReservationService.confirmReservation(userId, reservationId, paidAmount, customerRequest)` →
  `Reservation.confirm(paidAmount, now, customerRequest)`
  - 앞뒤 공백을 제거해 저장한다. null이거나 공백뿐이면 저장하지 않는다(null).
  - 1,000자를 넘으면 `COMMON_400`(INVALID_INPUT)이며 확정·저장하지 않는다.
- **결제 쪽 연결 (결제 담당, 미구현)**: `PaymentVerifyRequest`에 `customerRequest`를 추가하고,
  결제 확인을 마친 뒤 위 `confirmReservation`에 그대로 넘긴다.
- 기존 `CUSTOMER_REQUEST` 컬럼(`VARCHAR2(1000)`)을 사용하므로 스키마 변경은 없다.
- 예약 생성 요청(`ReservationCreateRequest`)에서는 `customerRequest`를 뺐다.
- 현재 상태: 프론트는 결제 화면에서 요청사항을 입력받아 상태로 들고 있다. 결제 요청·결제 확인 API 연결은
  결제 담당 작업에서 진행한다.

## 오류

| 코드 | 상태 | 조건 |
|---|---|---|
| COMMON_400 | 400 | 입력 검증 실패 (필수값 누락, 잘못된 page·size 등) |
| VACANCY_003 | 400 | 오늘부터 60일 이내가 아님 |
| VACANCY_004 | 400 | 시작 시각이 30분 단위가 아님 |
| COMMON_401 | 401 | 비로그인 |
| COMMON_403 | 403 | 역할이 맞지 않음 (생성·내 예약은 고객, 매장 예약은 사장님) |
| COMMON_404 | 404 | 없는 예약 또는 다른 고객·다른 매장의 예약 (상세) |
| TREATMENT_001 | 404 | 없는 시술 또는 판매 중이 아닌 시술 |
| VACANCY_001 | 409 | 예약 접수 마감 (시작 20분 전 이후) |
| VACANCY_006 | 409 | 매장의 다른 예약과 시간이 겹침 |
| VACANCY_013 | 409 | 영업하지 않는 시간이거나 닫힌 시간 |
| VACANCY_016 | 409 | 본인의 다른 예약과 시간이 겹침 |

결제 확정 단계의 오류(RESERVATION_001~005, VACANCY_002)는 결제 확인 API에서 발생한다.

## 화면 연결

- 요청 순서: 화면 → `useReservationStore` → `reservationApi` → `axiosInstance`.
- `/reserve/:id`: 예약하기 버튼이 `createReservation`을 호출하고, 성공하면 `/checkout?reservationId={번호}`로 이동한다.
  실패하면 화면에 머물며 서버 오류 메시지를 보여준다.
  - 시간 선택 화면은 아직 시연용 시술 주소(`/reserve/nail` 등)를 쓴다. `utils/demoData.js`의 `treatmentId`로
    초기 데이터의 실제 시술(nail→1, art→2, hair→3, clinic→4)과 연결했고, 번호가 없는 시술은 예약 버튼을 막는다.
  - 빈 시간 표시는 아직 시연용 계산이다. 실제로 비어 있지 않은 시간은 서버가 거절한다.
- `/checkout?reservationId={번호}`: 내 예약 상세 API로 예약을 불러와 매장·시술·시각·금액·예약금·잔금을 표시한다.
  - 남은 시간은 `holdExpiresAt` 기준이며 새로고침해도 유지된다. 만료되면 결제 버튼 대신 시간 다시 선택하기를 보여준다.
  - CONFIRMED면 내 예약으로, 없는·남의 예약이면 빈자리 탐색·내 예약으로, 비로그인이면 로그인으로 안내한다.
- 로컬 확인은 개발용 테스트 계정 전환(`CUSTOMER_1` 등, `Authorization: Bearer {테스트 사용자}`)을 사용한다.

## 검증 결과 (UNNIE-92, 2026-10-28)

- `frontend/`: `npm run lint`, `npm run build` 통과.
- 백엔드를 local 프로필·H2 메모리 DB로 기동해 실제 화면으로 확인:
  예약 생성 → 결제 화면 표시, 새로고침 유지, 5분 만료 안내, 같은 시간 재예약 거절(VACANCY_006),
  비로그인 거절(COMMON_401), 없는 예약·다른 고객 예약 안내.
- `backend/`: `.\gradlew.bat --no-daemon test` 통과 (전체 572개, 실패 0개).
  확정 시 요청사항 저장·공백 제거·null·공백 처리·1,000자 경계 테스트를 추가했다.
- 결제 확인 API에서 요청사항을 넘기는 부분은 미구현이라 화면에서 끝까지 저장되는 흐름은 검증하지 않았다.
