# 영업시간·휴무·시간대 닫기 (UNNIE-242)

매장 계정 전용이다. 빈자리는 `영업시간 + 시간대 열기 − 시간대 닫기 − 예약`으로 계산한다.

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/v1/business-hours` | 월~일 영업시간 |
| GET | `/api/v1/business-hours/closing-impact?days=THU&days=FRI` | 휴무로 바꾸기 전 그 요일의 앞으로의 예약과 열기 |
| PUT | `/api/v1/business-hours` | 월~일 영업시간 저장 |
| POST | `/api/v1/schedule-exceptions` | 시간대 열기·닫기 등록 (201) |
| GET | `/api/v1/schedule-exceptions?from=&to=` | 시간대 열기·닫기 목록 |
| PUT | `/api/v1/schedule-exceptions/{exceptionId}` | 시간대 열기·닫기 수정 |
| DELETE | `/api/v1/schedule-exceptions/{exceptionId}` | 시간대 열기·닫기 삭제 (204) |

모두 `ApiResponse<T>` 형식이며, 고객·관리자는 `COMMON_403`, 비로그인은 `COMMON_401`이다.

## 영업시간 조회·저장

응답 `data`는 월~일 7개다. 저장하지 않은 요일은 휴무(`closed: true`, 시각 null)로 채운다.

```json
[
  { "dayOfWeek": "MON", "closed": false, "openTime": "10:00", "closeTime": "20:00" },
  { "dayOfWeek": "SUN", "closed": true, "openTime": null, "closeTime": null }
]
```

저장 요청:

```json
{
  "days": [
    { "dayOfWeek": "MON", "closed": false, "openTime": "10:00", "closeTime": "20:00" },
    { "dayOfWeek": "TUE", "closed": false, "openTime": "10:00", "closeTime": "20:00" },
    { "dayOfWeek": "WED", "closed": false, "openTime": "10:00", "closeTime": "20:00" },
    { "dayOfWeek": "THU", "closed": true, "openTime": null, "closeTime": null },
    { "dayOfWeek": "FRI", "closed": false, "openTime": "10:00", "closeTime": "20:00" },
    { "dayOfWeek": "SAT", "closed": false, "openTime": "10:00", "closeTime": "18:00" },
    { "dayOfWeek": "SUN", "closed": true, "openTime": null, "closeTime": null }
  ],
  "closeOpenExceptions": true
}
```

| 필드 | 필수 | 설명 |
|---|---|---|
| `days` | O | `MON`~`SUN` 7개, 요일마다 한 번씩. 어기면 `VACANCY_012` |
| `days[].closed` | O | `true`면 휴무 |
| `days[].openTime`, `closeTime` | 영업일만 | `HH:mm`, 30분 단위(`VACANCY_004`), 종료가 시작보다 뒤(`VACANCY_005`). 자정을 넘길 수 없다. 영업일에 없으면 `VACANCY_014` |
| `closeOpenExceptions` | X | 아래 [요일 휴무](#요일-휴무) |

응답 `data`: 저장한 7개 요일 (조회와 같은 모양).

## 요일 휴무

- 요일을 휴무로 바꾸면 **새 예약만 막고 이미 받은 예약은 유지**한다. 먼 미래의 예약 한 건 때문에 휴무를 저장하지 못하는 일을 막기 위해서다. 그날 쉬려면 사장님이 예약을 따로 취소한다.
- 영업하는 요일의 시간을 줄여 예약이 밖으로 밀려나면 지금처럼 `VACANCY_011`로 거절하고, 메시지에 예약 시각을 담는다.
- 저장 요청의 `closeOpenExceptions: true`는 **이번에 휴무로 바뀐 요일**의 열기 중 예약이 없는 것을 지운다. 예약이 있는 열기, 원래 휴무였던 요일에 연 시간, 다른 요일까지 이어진 열기는 지우지 않는다.
- 지우지 않은 열기는 휴무여도 계속 판매한다. 휴무일에 연 시간은 `연장`으로 표시한다.

`closing-impact` 응답:

```json
{
  "reservations": [{ "startsAt": "2026-11-26T14:00:00+09:00", "endsAt": "2026-11-26T15:00:00+09:00" }],
  "openExceptions": [
    { "exceptionId": 3, "startsAt": "2026-11-26T21:00:00+09:00", "endsAt": "2026-11-26T23:00:00+09:00", "closable": true }
  ]
}
```

- 범위는 지금부터 예약 가능 기간(60일)까지다. 점유가 만료된 예약은 뺀다. 응답에 고객 정보는 담지 않는다.
- `closable`은 함께 닫을 수 있는지다. 예약이 있거나 조회한 요일 밖까지 이어진 열기는 `false`다.

## 시간대 열기·닫기 등록·수정

```json
{
  "exceptionType": "CLOSE",
  "startsAt": "2026-11-26T12:00:00+09:00",
  "endsAt": "2026-11-26T13:00:00+09:00",
  "reason": "점심시간",
  "excludeReserved": false
}
```

| 필드 | 필수 | 설명 |
|---|---|---|
| `exceptionType` | O | `OPEN`(영업시간 밖을 더 엶, 연장 영업) 또는 `CLOSE`(영업시간 안을 닫음) |
| `startsAt`, `endsAt` | O | 30분 단위(`VACANCY_004`), 종료가 시작보다 뒤(`VACANCY_005`), 종료가 아직 지나지 않고 시작일이 오늘 포함 60일 이내(`VACANCY_003`). 열기는 시작 30분 전까지만 등록·수정(`VACANCY_009`) |
| `reason` | X | 500자 이하. 고객에게는 보여주지 않는다 |
| `excludeReserved` | X | 닫기 등록만. 아래 [시간대 닫기](#시간대-닫기) |

응답 `data`:

```json
{
  "exceptionId": 3,
  "exceptionType": "CLOSE",
  "startsAt": "2026-11-26T12:00:00+09:00",
  "endsAt": "2026-11-26T13:00:00+09:00",
  "reason": "점심시간",
  "reservationId": null
}
```

- `reservationId`는 매장 취소(`cancel.md`, `closeAfterCancel: true`)로 생긴 닫기일 때 그 예약 ID다. 직접 등록한 일정은 null이다.
- 같은 종류(열기끼리, 닫기끼리)의 일정과 시간이 겹치면 `VACANCY_017`(409).
- 닫기 구간, 또는 수정·삭제로 줄어드는 열기 구간에 점유·확정 예약이 있으면 `VACANCY_011`(409).
- 없는 일정은 `VACANCY_010`(404), 다른 매장의 일정은 `COMMON_403`.

## 시간대 열기·닫기 목록 `GET /api/v1/schedule-exceptions?from=2026-11-01&to=2026-11-30`

- `from`, `to`(`yyyy-MM-dd`) 모두 필수. `[from 00:00, to 다음 날 00:00)`과 겹치는 일정을 시작 시각 순으로 돌려준다.
- `to`가 `from`보다 앞이면 `VACANCY_005`(400).
- 응답 `data`: 위 응답 모양의 배열.

## 시간대 열기·닫기 삭제 `DELETE /api/v1/schedule-exceptions/{exceptionId}`

- 성공하면 본문 없이 204.
- 열기 구간에 예약이 있으면 `VACANCY_011`(409). 닫기는 언제든 지울 수 있다.

## 시간대 닫기

- 닫기 등록 요청의 `excludeReserved: true`는 예약이 있는 시간을 뺀 나머지 구간만 닫는다. 구간마다 닫기 한 건을 저장하고 응답은 첫 건이다.
- 전 구간에 예약이 있으면 `VACANCY_011`이다. 값을 보내지 않으면 예약이 있을 때 지금처럼 거절한다. 수정에는 적용하지 않는다.
