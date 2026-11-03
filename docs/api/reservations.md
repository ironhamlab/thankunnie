# 예약 (D 예약 생성·조회 / UNNIE-92 예약 → 결제 화면 연결)

고객이 시술과 시작 시각을 고르면 5분간 시간을 점유(HOLD)하는 예약을 만들고,
예약금 결제가 확인되면 예약 완료(CONFIRMED)로 바꾼다.
현재 사용자는 B-01의 `@LoginUser AuthUser`를 사용한다. 모든 JSON 응답은 `ApiResponse<T>` 형식이다.
시각은 모두 한국 시간 `+09:00` 오프셋의 ISO-8601 문자열이다.

## API

| 메서드 | 경로 | 동작 | 권한 | 성공 |
|---|---|---|---|---|
| POST | `/api/v1/reservations` | 예약 생성(점유) | 고객 | 201 |
| POST | `/api/v1/reservations/queue` | 예약 대기열 서기·순서 조회 | 고객 | 200 |
| POST | `/api/v1/reservations/queue/leave` | 예약 대기열 나가기 | 고객 | 204 |
| GET | `/api/v1/my/reservations` | 내 예약 목록 | 고객 | 200 |
| GET | `/api/v1/my/reservations/{reservationId}` | 내 예약 상세 | 고객 | 200 |
| GET | `/api/v1/my/reservations/current-hold` | 내 현재 점유 | 고객 | 200 |
| POST | `/api/v1/my/reservations/{reservationId}/release` | 내 점유 풀기 | 고객 | 204 |
| GET | `/api/v1/owner/reservations` | 본인 매장 예약 목록 | 매장 | 200 |
| GET | `/api/v1/owner/reservations/{reservationId}` | 본인 매장 예약 상세 | 매장 | 200 |

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

고객 요청사항은 이 요청에서 받지 않는다. 결제 화면에서 받아 예약 확정 때 저장한다 ([요청사항 저장](#요청사항-저장) 참고).

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

## 예약 대기열 (UNNIE-174)

알림을 받은 여러 고객이 같은 빈자리를 동시에 누르면, 누른 순서대로 번호표를 주고 한 명씩 예약하게 한다.
빈자리 하나는 자리 하나이므로 줄은 **같은 시술 + 같은 시작 일시** 단위로 나눈다.

- 맨 앞 사람의 차례(`MY_TURN`)가 되면 예약 생성(점유)을 부른다. 점유하면 그 사람은 줄에서 빠지고, 뒷사람은 결제 중(`paying`)으로 기다린다.
- 점유가 결제 없이 풀리면(점유 풀기·5분 만료) 다음 사람 차례가 된다. 확정되거나 마감·닫힘이면 모두 `CLOSED`를 받는다.
- 같은 시간 두 명 점유를 막는 것은 여전히 예약 생성의 회원·매장 잠금이다. 대기열은 순서만 정한다.
- 줄은 서버 메모리에 둔다(백엔드 1대 기준). 재시작·배포하면 줄이 비워지고 다시 선다. 점유·예약은 DB에 있어 영향이 없다.
- 기다리는 사람이 없는 빈자리는 줄에 서지 않고 예약해도 된다. 기다리는 사람이 있으면 맨 앞 사람만 예약할 수 있다(`RESERVATION_007`).
- 이미 다른 고객이 결제 중인 시간도 고를 수 있다. 가능 시간 응답의 `heldStartTimes`(A-F1)에 오고, 빈자리 상세는 `notBookableReason=HELD`로 알린다.
  매장 상세는 그 시간 칸에 "결제 중" 표시를 붙이고, 예약하기 대신 대기하기 버튼으로 줄에 세운다.

| 규칙 | 값 |
|---|---|
| 순서 다시 묻기(화면) | 2초마다 |
| 줄에서 빠짐 | 15초 동안 순서를 묻지 않으면 (대기 화면을 닫음·탭 종료) |
| 차례 유지 | 차례가 온 뒤 15초 안에 예약하지 않으면 다음 사람에게 넘어감 |
| 빈자리 상태 조회 | 같은 줄은 1초에 한 번만 DB에서 읽음 |
| 빈 줄 정리 | 1분마다 |

### 줄 서기·순서 조회 `POST /api/v1/reservations/queue`

요청은 예약 생성과 같은 빈자리다. 이미 서 있으면 순서를 바꾸지 않고 지금 순서를 돌려준다.

```json
{ "treatmentId": 1, "startsAt": "2026-10-29T15:00:00+09:00" }
```

응답 `data` (200):

```json
{
  "status": "WAITING",
  "aheadCount": 2,
  "paying": true,
  "turnExpiresAt": null,
  "reservationId": null,
  "serverTime": "2026-10-29T13:00:02.120+09:00"
}
```

| `status` | 뜻 | 화면 |
|---|---|---|
| WAITING | 앞사람이 있거나 앞사람이 결제 중 | 대기 화면에 `aheadCount`(결제 중인 사람 제외)와 결제 중 여부 표시 |
| MY_TURN | 내 차례. `turnExpiresAt` 전에 예약해야 함 | 바로 예약 생성 → 결제 화면 |
| HOLDING | 내가 이미 이 빈자리를 점유 중 (`reservationId`) | 결제 화면으로 이동 |
| CLOSED | 확정·마감·닫힘으로 더 기다려도 예약 불가 | 다른 시간을 고르라고 안내 |

### 줄 나가기 `POST /api/v1/reservations/queue/leave`

- 요청은 줄 서기와 같다. 줄에 없어도 204.
- 화면은 대기 취소, 대기 중 다른 화면으로 이동할 때 부른다. 실패해도 15초 뒤 서버가 줄에서 뺀다.

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

- 고객은 본인 예약만, 매장은 본인 매장 예약만 조회한다. 다른 사람의 예약은 존재 여부를 드러내지 않도록 404다.
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
  "cancel": null,
  "serverTime": "2026-10-28T16:12:40.120+09:00"
}
```

| 필드 | 설명 |
|---|---|
| `summary` | 목록과 같은 요약. `balance`는 현장 결제 잔금(적용 판매가 − 예약금) |
| `summary.cancellable` | 취소 버튼 표시 여부. 확정 상태이고 시작 전이면 true. 실제 취소 가능 판단은 취소 API가 한다 |
| `payment` | 최근 예약금 결제. `status`(READY, PAID, FAILED, UNKNOWN, REFUND_PENDING, PART_REFUNDED, REFUNDED), `amount`, `refundedAmount`, `approvedAt`, `refundedAt`. 결제 전이면 null |
| `cancel` | 취소. `cancelType`(CUSTOMER, SHOP_FAULT, CUSTOMER_REQUEST), `status`, `policyRefundAmount`, `refundAmount`, `requestedAt`, `completedAt`. 취소 전이면 null |
| `serverTime` | 응답을 만든 서버 시각. 화면은 기기 시각과의 차이를 보정해 `holdExpiresAt`까지 남은 시간을 센다 |

## 예약 상태

| 상태 | 뜻 |
|---|---|
| HOLD | 점유. 예약 클릭 후 결제 대기 (5분) |
| CONFIRMED | 예약 완료. 결제 확정 |
| CANCEL_PENDING | 취소 처리 중. 환불 완료 전까지 시간을 계속 잡고 있다 |
| CANCELLED | 취소 완료 |
| EXPIRED | 점유 만료. 5분 안에 결제하지 않았거나, 결제 화면을 떠나 점유를 풀었음 |

## 점유 풀기·교체 규칙 (UNNIE-138 구현)

결제 화면을 떠난 고객이 시간을 계속 잡고 있지 않도록, 그리고 한 고객이 여러 시간을 잡지 않도록 정한 규칙이다.

| # | 규칙 |
|---|---|
| R1 | 앱 안에서 결제 화면(`/checkout`)을 떠나면 점유를 푼다 (화면 버튼, 브라우저 뒤로가기, 헤더 등 다른 링크) |
| R2 | 떠나기 전에 확인창을 띄운다. 취소하면 결제 화면에 남는다 |
| R3 | 새로고침은 점유를 유지한다. 탭 닫기·주소 직접 입력처럼 앱 밖으로 나가면 5분 만료에 맡긴다 |
| R4 | 풀 때는 행을 지우지 않고 `STATUS`를 EXPIRED로, `HOLD_EXPIRES_AT`을 현재 시각으로 바꾼다 |
| R5 | 결제 진행 중이거나 결제 완료 후 이동할 때는 막지도 풀지도 않는다 |
| R6 | 고객 한 명은 점유를 1개만 가진다. 점유가 있는데 다른 시간을 누르면 확인창 후 교체한다. 같은 시술·같은 시간을 누르면 기존 점유를 그대로 돌려준다(만료 시각 유지). 교체에 실패하면 기존 점유가 남는다 |
| R7 | 결제 화면은 탭이 다시 보일 때와 결제 직전에 예약 상태를 다시 조회한다. 다른 탭에서 풀렸거나 교체됐으면 만료 안내를 보여준다 |

### 점유 풀기 `POST /api/v1/my/reservations/{reservationId}/release`

- 고객 본인의 예약만. 다른 사람의 예약·없는 예약은 `RESERVATION_001`(404).
- 예약 행을 잠근 뒤 상태를 다시 확인한다 (결제 확정과 동시에 와도 한쪽만 반영).
  - HOLD → EXPIRED로 바꾸고 `HOLD_EXPIRES_AT`을 현재 시각으로 바꾼다.
  - 이미 EXPIRED → 아무것도 하지 않고 성공 (중복 호출·만료 직후 호출).
  - CONFIRMED → `RESERVATION_002`(409), CANCEL_PENDING·CANCELLED → `RESERVATION_004`(409).
- 성공 응답은 204, 본문 없음.
- 프론트는 실패해도 이동은 막지 않는다. 남은 점유는 5분 뒤 만료된다.
- 알림 영향 없음: 빈자리 재공개(REOPENED) 알림은 CANCELLED만 대상이다. 풀린 시간은 임박·초임박 알림 스캔에서 빈자리로 다시 잡힌다.

### 예약 생성의 점유 교체 (R6)

요청에 `replaceReservationId`(선택)를 추가한다. 고객이 확인창에서 보고 풀기로 한 기존 점유 예약 ID다.

```json
{
  "treatmentId": 1,
  "startsAt": "2026-10-31T16:00:00+09:00",
  "replaceReservationId": 1002
}
```

1. 회원 잠금(`UserLock`) 뒤 이 고객의 유효한 HOLD(만료 전)를 행 잠금으로 찾는다.
2. 없으면 지금과 같이 만든다.
3. 있고 같은 시술·같은 시작 시각이면 `replaceReservationId`와 관계없이 기존 점유를 그대로 돌려준다.
   만료 시각을 늘리지 않아 반복 클릭으로 무한 점유할 수 없다.
4. 있고 다른 시술·시각이며 `replaceReservationId`가 없거나 지금 점유의 ID와 다르면 `RESERVATION_006`(409).
   확인창을 띄운 뒤 다른 탭에서 점유가 바뀌었으면 사용자가 본 것과 다른 점유를 풀지 않고 다시 묻게 한다.
   오류 응답은 공통 형식(코드·메시지)만 담는다.
5. 있고 다른 시술·시각이며 `replaceReservationId`가 지금 점유의 ID와 같으면 기존 점유를 EXPIRED로 바꾸고,
   가용 시간을 검사해 새 점유를 만든다.
6. 5는 한 트랜잭션이다. 새 시간이 이미 차 있어 실패하면 전체가 롤백되어 기존 점유가 남는다.

### 현재 점유 조회 `GET /api/v1/my/reservations/current-hold`

- 고객이 지금 잡고 있는 결제 대기 예약(HOLD, 만료 시각 전)의 상세를 돌려준다. 응답 모양은 내 예약 상세와 같다.
- 없으면 200이고 `data`가 null이다.
- 1인 1점유 검사와 같은 기준이라, 만료 정리(60초 주기) 전인 HOLD는 보여주지 않는다.

매장 상세 화면은 `RESERVATION_006`을 받으면 현재 점유 조회로 기존 점유를 찾아
"결제 대기 중인 예약(매장·시각)이 있어요. 이 시간으로 바꿀까요?" 확인창을 띄운다.
[이 시간으로 바꾸기] → 그 예약 ID를 `replaceReservationId`로 재요청, [기존 예약 결제하기] → 기존 예약의 `/checkout?reservationId=`로 이동.
조회에 실패하면 바꾸지 않고 오류를 보여준다. 점유가 없으면(그 사이 만료) 바꾸기 없이 한 번 다시 보낸다.

### 이동 감지 방식 (프론트)

`react-router-dom`의 `useBlocker`로 앱 안 이동(버튼, 뒤로가기, 다른 링크)을 한곳에서 잡는다.
`useBlocker`는 data router에서만 동작하므로 `App.jsx`의 `BrowserRouter`를 `createBrowserRouter` + `RouterProvider`로 바꿨다 (프론트 파트장 확인, 적용 완료).
새 라이브러리 추가는 없고, `AppRouter.jsx`의 `<Routes>`는 그대로 둔다.

```jsx
const router = createBrowserRouter([
  { path: '*', element: (<><AppRouter /><DevTestUserSwitch /></>) },
]);

export default function App() {
  return <RouterProvider router={router} />;
}
```

- 결제 화면에서 `useBlocker`가 이동을 막으면 확인창을 띄운다.
  확인 → 풀기 API → `blocker.proceed()`, 취소 → `blocker.reset()`으로 결제 화면에 남는다.
- 결제 진행 중·결제 완료 이동(R5)은 막지 않도록 `useBlocker` 조건에서 뺀다.
- 새로고침·탭 닫기는 앱 안 이동이 아니라 `useBlocker`가 잡지 않는다 (R3과 같음).

### 알려진 한계

- 탭을 닫거나 앱 밖으로 나가면 최대 5분 동안 그 시간이 잡혀 있다.
- 여러 탭에서 같은 점유를 공유한다. 한 탭에서 풀면 다른 탭은 R7 재조회 때 만료로 바뀐다.
- 만료 뒤 같은 시간을 다시 누르면 새 점유가 생긴다 (재점유 대기 시간은 두지 않음).
- 결제창이 열린 채 5분이 지나면 결제는 끝까지 진행될 수 있다. 승인 뒤 서버 확정이 거절되고 [자동 취소](#결제-확인--예약-확정-unnie-142)된다.
- 포트원 웹훅이 없어 결제 확인은 브라우저가 `verify`를 불러야 시작된다. 승인 뒤 서버에 알리기 전에 브라우저가 꺼지면 예약도 취소도 없이 결제만 남을 수 있다.
- 자동 취소가 실패하면 서버 로그로만 남고 고객 안내는 그대로다. 담당자 알림과 재시도는 두지 않았다.

## 결제 확인 → 예약 확정 (UNNIE-142)

`POST /api/v1/payments/verify` (고객 로그인 필요)

결제창(포트원 V1, KG이니시스)이 끝나면 프론트가 결제 고유번호와 예약 ID를 보낸다. 서버는 결제를 확인하고 예약을 확정한다.

```json
{
  "impUid": "imp_123456789012",
  "reservationId": 10,
  "customerRequest": "손톱은 라운드로 부탁드려요"
}
```

- 결제창 주문번호(`merchant_uid`)는 `reservation-{reservationId}-{고유값}` 으로 만든다. 서버는 이 앞부분으로 결제와 예약의 짝을 확인한다.
- 결제 금액은 예약의 `deposit`(예약금)과 같아야 한다.
- 처리 순서
  1. 고객이 아니면 `FORBIDDEN`
  2. 포트원에서 결제 조회 → 상태가 `paid` 가 아니면 `PAYMENT_006`, 주문번호가 이 예약 것이 아니면 `PAYMENT_007`
  3. 한 트랜잭션에서 예약 확정(`confirmReservation`, 요청사항 저장 포함)과 예약금결제(PAID) 저장
  4. 확정할 수 없으면 결제를 **바로 포트원에서 전액 취소**하고 확정 오류를 그대로 돌려준다:
     `RESERVATION_002`(이미 확정), `RESERVATION_003`(점유 만료), `RESERVATION_004`, `RESERVATION_005`(금액 불일치), `VACANCY_002`(결제 마감).
     모두 예약 주인을 확인한 뒤에 나는 오류다.
     `RESERVATION_001`(없는 예약·다른 고객의 예약)은 취소하지 않는다. 다른 고객이 남의 결제를 취소시키지 못하게 하려는 것이며, 이런 결제는 운영자가 확인해 수동 환불한다.
     요청사항이 1,000자를 넘는 `COMMON_400` 은 고쳐서 같은 결제로 다시 확인할 수 있으므로 취소하지 않는다.
     자동 취소가 실패하면 서버 로그(`수동 확인 필요`)에 남긴다.
- 같은 결제를 다시 보내면(새로고침·중복 클릭) 다시 확정하지 않고 같은 결과를 돌려준다. 동시에 두 번 와서 한쪽이 먼저 확정한 경우도 성공으로 본다.
  이때도 예약 주인을 확인하며, 다른 고객이 보내면 `RESERVATION_001` 이다.

응답 `ApiResponse<PaymentVerifyResponse>`

| 필드 | 설명 |
|---|---|
| `reservationId` | 확정된 예약 ID. 결과 화면 이동에 쓴다 |
| `depositPaymentId` | 저장된 예약금결제 ID |
| `impUid`, `merchantUid`, `amount`, `status`, `payMethod`, `paidAt` | 포트원 결제 정보 (`paidAt` 은 +09:00) |

## 요청사항 저장

결제 연결은 UNNIE-142에서 완료했다.

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
- **결제 쪽 연결 (완료, UNNIE-142)**: `PaymentVerifyRequest`에 `customerRequest`를 추가하고,
  결제 확인을 마친 뒤 위 `confirmReservation`에 그대로 넘긴다.
- 기존 `CUSTOMER_REQUEST` 컬럼(`VARCHAR2(1000)`)을 사용하므로 스키마 변경은 없다.
- 예약 생성 요청(`ReservationCreateRequest`)에서는 `customerRequest`를 뺐다.
- 현재 상태: 서버와 프론트 결제 화면 모두 연결했다. 결제 화면에서 입력한 요청사항을 결제 확인 요청에 담아 보낸다.

## 오류

| 코드 | 상태 | 조건 |
|---|---|---|
| COMMON_400 | 400 | 입력 검증 실패 (필수값 누락, 잘못된 page·size 등) |
| VACANCY_003 | 400 | 오늘부터 60일 이내가 아님 |
| VACANCY_004 | 400 | 시작 시각이 30분 단위가 아님 |
| COMMON_401 | 401 | 비로그인 |
| COMMON_403 | 403 | 역할이 맞지 않음 (생성·내 예약은 고객, 매장 예약은 매장) |
| COMMON_404 | 404 | 없는 예약 또는 다른 고객·다른 매장의 예약 (상세) |
| TREATMENT_001 | 404 | 없는 시술 또는 판매 중이 아닌 시술 |
| VACANCY_001 | 409 | 예약 접수 마감 (시작 20분 전 이후) |
| VACANCY_006 | 409 | 매장의 다른 예약과 시간이 겹침 |
| VACANCY_013 | 409 | 영업하지 않는 시간이거나 닫힌 시간 |
| VACANCY_016 | 409 | 본인의 다른 예약과 시간이 겹침 |
| TERMS_004 | 409 | 초보 시술인데 촬영(`MODEL_PHOTO`) 동의가 없음. 동의 모달 → `PUT /api/v1/users/me/terms-agreements/MODEL_PHOTO` → 예약 생성 재요청 (`terms.md`) |
| RESERVATION_006 | 409 | 다른 시간의 결제 대기 예약이 있는데 그 예약 ID를 `replaceReservationId`로 보내지 않고 생성 |
| RESERVATION_007 | 409 | 같은 빈자리 대기열에 먼저 선 사람이 있어 아직 내 차례가 아님 (차례 시간 초과 포함) |
| RESERVATION_001 | 404 | 없는 예약 또는 다른 고객의 예약 (점유 풀기) |
| RESERVATION_002 | 409 | 이미 확정된 예약 (점유 풀기·교체) |
| RESERVATION_004 | 409 | 취소 처리 중·취소 완료 예약 (점유 풀기·교체) |

결제 확정 단계의 오류(RESERVATION_001~005, VACANCY_002)는 결제 확인 API에서 발생한다.

## 화면 연결

- 요청 순서: 화면 → `useReservationStore` → `reservationApi` → `axiosInstance`.
- `/shops/:shopId`: 시술·날짜·시간을 고르고 예약하기 버튼을 누르면 먼저 대기열에 선다(`joinQueue`).
  내 차례면 `createReservation`을 호출하고 성공하면 `/checkout?reservationId={번호}`로 이동한다.
  앞사람이 있으면 대기 창(`QueueWaitingDialog`)을 띄우고 2초마다 순서를 다시 물어, 차례가 오면 자동으로 예약한다.
  실패하면 화면에 머물며 서버 오류 메시지를 보여준다. `/reserve/:id`도 같은 훅(`useReserve`)을 쓴다.
- `/reserve/:id`: 시연용 시술 주소(`/reserve/nail` 등)를 쓰는 예전 예약 화면. `utils/demoData.js`의 `treatmentId`로
  초기 데이터의 실제 시술과 연결했고, 빈 시간 표시는 시연용 계산이라 비어 있지 않은 시간은 서버가 거절한다.
- `/checkout?reservationId={번호}`: 내 예약 상세 API로 예약을 불러와 매장·시술·시각·금액·예약금·잔금을 표시한다.
  - 저장소의 상세는 다른 화면에서 보던 예약일 수 있으므로 주소의 예약 번호와 같을 때만 쓴다.
  - 남은 시간은 `holdExpiresAt` 기준이며 새로고침해도 유지된다. 만료되면 결제 버튼 대신 시간 다시 선택하기를 보여준다.
  - 기기 시계가 서버와 달라도 남은 시간이 서버 기준이 되도록, 응답의 `serverTime`과 기기 시각의 차이만큼 보정해 센다.
  - CONFIRMED면 내 예약으로, 없는·남의 예약이면 빈자리 탐색·내 예약으로, 비로그인이면 로그인으로 안내한다.
- 로컬 확인은 개발용 테스트 계정 전환(`CUSTOMER_1` 등, `Authorization: Bearer {테스트 사용자}`)을 사용한다.

## 검증 결과 (UNNIE-92, 2026-10-28)

- `frontend/`: `npm run lint`, `npm run build` 통과.
- 백엔드를 local 프로필·H2 메모리 DB로 기동해 실제 화면으로 확인:
  예약 생성 → 결제 화면 표시, 새로고침 유지, 5분 만료 안내, 같은 시간 재예약 거절(VACANCY_006),
  비로그인 거절(COMMON_401), 없는 예약·다른 고객 예약 안내.
- `backend/`: `.\gradlew.bat --no-daemon test` 통과 (전체 572개, 실패 0개).
  확정 시 요청사항 저장·공백 제거·null·공백 처리·1,000자 경계 테스트를 추가했다.
- 이 검증 당시에는 결제 확인 API에서 요청사항을 넘기는 부분이 미구현이라 화면에서 끝까지 저장되는 흐름은 검증하지 않았다 (이후 UNNIE-142에서 연결).
