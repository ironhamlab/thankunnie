# 알림 구독(찜) API

고객이 매장 또는 카테고리·지역을 구독해 두면, 희망 시간 안에 빈자리가 생겼을 때 문자와 받은 알림(`notification-inbox.md`)으로 알려준다.
희망 시간 없이 매장만 구독하면 알림 없는 "찜"이다. 모든 API는 **고객 전용**이며, 모든 JSON 응답은 `ApiResponse<T>` 형식이다.

| 메서드 | 경로 | 동작 | 성공 |
|---|---|---|---|
| POST | `/api/v1/notifications/subscriptions` | 구독 등록 | 201 |
| GET | `/api/v1/notifications/subscriptions` | 내 구독 목록 (해제하지 않은 것, 최신 등록 순) | 200 |
| PUT | `/api/v1/notifications/subscriptions/{subscriptionId}` | 구독 대상·희망 시간 수정 | 200 |
| DELETE | `/api/v1/notifications/subscriptions/{subscriptionId}` | 구독 해제 | 204 |

## 구독 유형

| `subscriptionType` | 필요한 값 | 넣으면 안 되는 값 |
|---|---|---|
| `STORE` (업장) | `shopId`. 희망 시간이 있으면 `treatmentId`도 필수 | `categoryId`, `regionCode` |
| `CATEGORY_REGION` (카테고리·지역) | `categoryId`, `regionCode`, `startTime`, `endTime` | `shopId`, `treatmentId` |

- 맞지 않으면 `NOTIFICATION_002`(400).
- `treatmentId`는 그 매장의 판매 중인 **일반(NORMAL)** 시술이어야 한다. 아니면 `NOTIFICATION_005`(400).
- `regionCode`는 시군구 5자리(`C-01-vacancy-search.md` 9장)다.

## 희망 시간

- `startTime`, `endTime`은 `+09:00` 오프셋의 ISO-8601 일시다. 둘 다 있거나 둘 다 없어야 하고, 시작이 종료보다 앞이어야 한다.
- 둘 다 비울 수 있는 것은 업장 찜뿐이다. 카테고리·지역 구독은 둘 다 필수다.
- `endTime`이 이미 지났거나 지금과 같으면 만료로 보고 거절한다.
- 위 조건을 어기면 `NOTIFICATION_003`(400).
- 희망 시간이 있는 구독은 문자가 나가므로 알림 문자 수신 동의(`SMS_NOTIFICATION`)가 필요하다. 없으면 `TERMS_003`(409). 화면은 동의 모달 → `PUT /api/v1/users/me/terms-agreements/SMS_NOTIFICATION` → 재요청 (`terms.md`).
- 등록하거나 희망 시간을 바꾸면, 이미 열려 있는 빈자리 중 희망 시간에 들어오는 것도 알림 대상으로 넣는다. 같은 빈자리·회원으로 이미 보낸 알림은 다시 보내지 않는다.

## 구독 등록 `POST /api/v1/notifications/subscriptions`

업장 알림:

```json
{
  "subscriptionType": "STORE",
  "shopId": 1,
  "treatmentId": 10,
  "startTime": "2026-10-28T10:00:00+09:00",
  "endTime": "2026-10-30T22:00:00+09:00"
}
```

희망 시간 없는 업장 찜은 `{ "subscriptionType": "STORE", "shopId": 1 }`만 보낸다. 문자 동의를 검사하지 않는다.

카테고리·지역 알림:

```json
{
  "subscriptionType": "CATEGORY_REGION",
  "categoryId": 1,
  "regionCode": "11680",
  "startTime": "2026-10-28T10:00:00+09:00",
  "endTime": "2026-10-30T22:00:00+09:00"
}
```

- 해제하지 않은 구독 중 같은 대상(업장은 매장·시술, 카테고리는 카테고리·지역)과 같은 희망 시작·종료가 있으면 `NOTIFICATION_004`(409). 해제한 구독은 다시 등록할 수 있다.

응답 `data` (201):

```json
{
  "subscriptionId": 5,
  "subscriptionType": "STORE",
  "shopId": 1,
  "treatmentId": 10,
  "categoryId": null,
  "regionCode": null,
  "startTime": "2026-10-28T10:00:00+09:00",
  "endTime": "2026-10-30T22:00:00+09:00",
  "status": "ACTIVE"
}
```

## 내 구독 목록 `GET /api/v1/notifications/subscriptions`

해제하지 않은(`ACTIVE`) 구독을 최신 등록 순으로 돌려준다. 응답 `data`는 위 응답 모양의 배열이다.
매장 상세의 찜 버튼은 이 목록에 같은 매장의 `STORE` 구독이 있으면 찜한 상태로 본다.

## 구독 수정 `PUT /api/v1/notifications/subscriptions/{subscriptionId}`

요청은 등록에서 `subscriptionType`을 뺀 것이다. 구독 유형은 바꿀 수 없으므로, 기존 유형에 맞는 대상과 희망 시간을 보낸다.

- 보낸 값 전체로 바꾼다. 비운 값은 지워진다.
- 찜(희망 시간 없음)에 희망 시간을 넣어 알림을 새로 켤 때만 문자 동의를 검사한다. 이미 알림이 켜진 구독의 시간만 바꿀 때는 검사하지 않는다.
- 중복 검사에서 자기 자신은 뺀다. 검사에 실패하면 아무것도 바뀌지 않는다.
- 응답 `data`: 수정된 구독.

## 구독 해제 `DELETE /api/v1/notifications/subscriptions/{subscriptionId}`

행을 지우지 않고 `STATUS`를 `INACTIVE`로 바꾼다. 해제한 구독은 목록과 알림 대상에서 빠진다. 성공하면 본문 없이 204.

## 오류

| 코드 | 상태 | 조건 |
|---|---|---|
| COMMON_400 | 400 | 입력 형식 오류 (`regionCode` 20자 초과 등) |
| COMMON_401 | 401 | 비로그인, 처리 중 탈퇴한 회원 |
| COMMON_403 | 403 | 고객이 아님 |
| NOTIFICATION_001 | 404 | 없는 구독, 다른 고객의 구독, 이미 해제한 구독 (수정·해제) |
| NOTIFICATION_002 | 400 | 구독 유형과 대상 값이 맞지 않음, 희망 시간이 있는 업장 알림에 `treatmentId` 없음 |
| NOTIFICATION_003 | 400 | 희망 시간이 하나만 있음, 시작 ≥ 종료, 이미 지난 종료, 카테고리 구독에 시간 없음 |
| NOTIFICATION_004 | 409 | 같은 대상·희망 시간의 구독이 이미 있음 |
| NOTIFICATION_005 | 400 | 그 매장의 판매 중인 일반 시술이 아님 |
| TERMS_003 | 409 | 알림 문자 수신 동의 없음 |
