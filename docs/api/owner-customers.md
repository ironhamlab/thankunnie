# 매장 고객 관리 API

이 매장에 예약한 고객의 이용 횟수·금액·예약 이력과 매장 전용 메모. 매장은 로그인한 매장 계정으로 정해진다.

- 권한: 매장(OWNER). 그 외 역할은 403, 비로그인은 401
- **이용** = 확정 예약 중 종료 시각이 지난 예약(이용 추정). 횟수·금액은 저장하지 않고 조회할 때마다 예약에서 계산한다
- 금액은 예약 시점 적용가 기준이다
- 분류(`segment`): 이 매장 누적 이용 0~1회 `NEW`(첫 방문 예정 포함), 2회 이상 `RETURNING`. 분석 화면의 신규·재방문과 같은 기준
- 이 매장에 확정·취소 완료 예약이 없는 고객은 상세·메모 모두 404 (`COMMON_404`)
- 연락처: 목록은 가운데를 가리고(`010-****-0106`), 상세에서만 전체를 준다 (매장 예약 상세와 같은 기준). 검색은 원래 번호로 하므로 뒷자리로 찾을 수 있다

| 메서드 | 경로 | 동작 |
| --- | --- | --- |
| GET | `/api/v1/owner/customers` | 고객 목록 |
| GET | `/api/v1/owner/customers/{userId}` | 고객 상세 |
| PUT | `/api/v1/owner/customers/{userId}/memo` | 매장 메모 저장·삭제 |

## GET /api/v1/owner/customers

| 파라미터 | 기본값 | 설명 |
| --- | --- | --- |
| `segment` | `ALL` | `ALL`, `NEW`, `RETURNING` |
| `sort` | `RECENT` | `RECENT`(최근 이용, 이용 없는 고객은 뒤), `VISITS`(이용 횟수), `AMOUNT`(누적 금액). 같으면 최근 이용 → 가까운 예정 예약 순 |
| `keyword` | | 이름 또는 연락처 일부 |
| `page`, `size` | `0`, `20` | size 1~100 |

확정 예약이 한 건이라도 있는 고객만 나온다 (취소만 한 고객은 목록에서 빠진다).

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "userId": 106, "name": "김하은", "phone": "010-****-0106",
        "visitCount": 7, "totalAmount": 238000,
        "firstVisitAt": "2025-11-08T14:00:00+09:00",
        "lastVisitAt": "2026-11-06T10:00:00+09:00",
        "nextReservationAt": "2026-11-20T10:00:00+09:00",
        "segment": "RETURNING", "hasMemo": true
      }
    ],
    "page": 0, "size": 20, "totalElements": 35, "totalPages": 2, "hasNext": true
  }
}
```

## GET /api/v1/owner/customers/{userId}

```json
{
  "success": true,
  "data": {
    "customer": { "userId": 106, "name": "김하은", "phone": "010-6666-0106", "...": "목록과 같은 필드 (연락처는 전체)" },
    "memo": { "memo": "누드톤 선호", "updatedAt": "2026-11-01T18:00:00+09:00" },
    "reservations": [
      {
        "reservationId": 590, "startsAt": "2026-11-06T10:00:00+09:00", "endsAt": "2026-11-06T11:00:00+09:00",
        "treatmentName": "젤네일 원컬러", "appliedPrice": 34000,
        "status": "COMPLETED", "cancelType": null, "customerRequest": "지난번과 같은 색으로 부탁드려요"
      }
    ]
  }
}
```

- `memo`: 없으면 `null`
- `reservations`: 이 매장 확정·취소 완료 예약을 최근 시작 순으로 최대 50건. `status` 는 `COMPLETED`(이용 추정)·`UPCOMING`(예정)·`CANCELLED`, `cancelType` 은 취소일 때만

## PUT /api/v1/owner/customers/{userId}/memo

```json
{ "memo": "누드톤 선호, 큐티클 정리 꼼꼼히" }
```

- 최대 1,000자. 넘으면 400
- 앞뒤 공백은 지운다. 비우거나 공백만 보내면 메모를 지우고 `data` 는 `null`
- 응답: `{ "memo": "...", "updatedAt": "..." }`
- 매장·고객당 메모 1개(`SHOP_CUSTOMER_MEMO`, `UNIQUE(SHOP_ID, USER_ID)`)이며 고객에게는 보이지 않는다
