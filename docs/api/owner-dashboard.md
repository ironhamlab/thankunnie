# 매장 대시보드·통계 API

매장 센터 첫 화면(`/owner`)과 통계 화면에 쓰는 지표. 매장은 로그인한 매장 계정으로 정해진다.

- 권한: 매장(OWNER). 그 외 역할은 403, 비로그인은 401
- 기준 시각: 서버 시각(한국 시간)
- 기간 지표는 **시술일(시작 날짜) 기준**이며 **확정(`CONFIRMED`)·취소 완료(`CANCELLED`)** 예약만 센다. 점유·만료·취소 처리 중은 제외
- 메인의 이번 달 요약(`month`)과 통계 요약(`summary`)은 같은 계산이라, 같은 기간이면 숫자가 같다

## GET /api/v1/owner/dashboard

### 응답 예시

```json
{
  "success": true,
  "data": {
    "date": "2026-11-02",
    "today": {
      "confirmedCount": 3,
      "upcomingCount": 1,
      "closed": false,
      "availableMinutes": 120,
      "availableRanges": [
        { "startsAt": "2026-11-02T15:00:00+09:00", "endsAt": "2026-11-02T16:30:00+09:00" },
        { "startsAt": "2026-11-02T19:30:00+09:00", "endsAt": "2026-11-02T20:00:00+09:00" }
      ]
    },
    "month": {
      "from": "2026-11-01",
      "to": "2026-11-30",
      "confirmedCount": 24,
      "bookedAmount": 720000,
      "customerCancelCount": 3,
      "shopFaultCancelCount": 1,
      "customerCancelRate": 10.7,
      "retainedDepositAmount": 30000,
      "rebookedAmount": 85000,
      "cancelledMinutes": 240,
      "rebookedMinutes": 180,
      "rebookRate": 75.0,
      "lastMinuteCount": 5,
      "lastMinuteAmount": 120000
    }
  }
}
```

### `today`

| 필드 | 의미 |
| --- | --- |
| `confirmedCount` | 오늘 시작하는 확정 예약 |
| `upcomingCount` | 그중 아직 시작하지 않은 예약 |
| `closed` | 오늘 영업시간·열기로 열린 시간이 전혀 없음(휴무). `false` 인데 `availableRanges` 가 비어 있으면 남은 시간이 모두 찬 것 |
| `availableMinutes` | 지금 예약받을 수 있는 남은 빈 시간(분) 합계 |
| `availableRanges` | 남은 빈 시간대 `[startsAt, endsAt)`. 고객 예약 화면과 같은 기준(영업시간·열기·닫기·기존 예약·시작 20분 전 접수 마감) |

빈 시간은 30분 단위로 계산한다. 시술 소요 시간보다 짧은 틈(예: 60분 시술만 있는 매장의 30분 틈)도 빈 시간에 포함된다.

### `month`

아래 [기간 요약 필드](#기간-요약-필드) 참고. 기간은 이번 달 1일~말일(앞으로 잡힌 예약 포함).

## GET /api/v1/owner/dashboard/stats

- 파라미터: `from`, `to` (`yyyy-MM-dd`, 둘 다 포함)
  - 둘 다 생략: 이번 달 1일~말일
  - `from` 만 생략: `to` 가 속한 달 1일 ~ `to`
  - `to` 만 생략: `from` ~ `from` 이 속한 달 말일
- 제한: `to` < `from` 이거나 기간이 92일을 넘으면 400 (`COMMON_400`)

### 응답 예시

```json
{
  "success": true,
  "data": {
    "summary": { "from": "2026-11-01", "to": "2026-11-30", "confirmedCount": 24, "...": "month 와 같은 필드" },
    "daily": [
      { "date": "2026-11-01", "confirmedCount": 0, "bookedAmount": 0, "cancelledCount": 0 },
      { "date": "2026-11-02", "confirmedCount": 3, "bookedAmount": 90000, "cancelledCount": 1 }
    ],
    "treatments": [
      { "treatmentId": 1, "treatmentName": "젤네일 원컬러", "confirmedCount": 12, "bookedAmount": 360000 }
    ]
  }
}
```

| 필드 | 의미 | 화면 |
| --- | --- | --- |
| `summary` | 기간 요약 | 상단 숫자 카드 |
| `daily` | 날짜별 확정 건수·확정 예약 금액·취소 완료 건수(유형 무관). 예약 없는 날도 0 으로 채워 기간 전체를 담음 | 라인 차트 |
| `treatments` | 시술별 확정 건수·확정 예약 금액. 금액 → 건수 많은 순 | 막대 차트 |

## 기간 요약 필드

| 필드 | 의미 | 화면 라벨 예시 |
| --- | --- | --- |
| `from`, `to` | 집계 기간 (둘 다 포함) | |
| `confirmedCount` | 확정 예약 | 확정 예약 |
| `bookedAmount` | 확정 예약 금액. 적용가 합계라 예약금과 현장 잔금을 포함 | 확정 예약 금액 |
| `customerCancelCount` | 고객 사정 취소 = 고객 직접 취소 + 고객 요청 대리 취소 | 고객 취소 |
| `shopFaultCancelCount` | 매장 사유 취소 | 매장 사유 취소 |
| `customerCancelRate` | 고객 취소 ÷ (확정 + 고객 취소 + 매장 사유 취소) × 100, 소수 첫째 자리. 대상이 없으면 `null` | 고객 취소율 |
| `retainedDepositAmount` | 취소 완료 건의 (납부 예약금 − 환불액) 합계 | 취소로 남은 예약금 |
| `rebookedAmount` | 취소로 비었던 시간에 다시 들어온 확정 예약의 금액 합계 | 취소 후 재판매 |
| `cancelledMinutes` | 취소로 비었던 시간(분) | |
| `rebookedMinutes` | 그중 다시 채운 시간(분) | |
| `rebookRate` | 다시 채운 시간 ÷ 비었던 시간 × 100, 소수 첫째 자리. 비었던 시간이 없으면 `null` | 회수율 |
| `lastMinuteCount`, `lastMinuteAmount` | 시작 3시간 이내에 점유한 확정 예약 건수·금액 (초임박가 판매) | 초임박 판매 |

비율이 `null` 이면 화면에서 `-` 로 표시한다.

### 재판매·회수율 계산

1. **비었던 시간**: 기간 안 취소 완료 예약의 시간을 합친 것(겹치면 한 번만). 매장이 취소하며 그 시간을 닫은 건(REO-02 닫기 선택)은 다시 팔 수 없으므로 뺀다.
2. **다시 채운 시간**: 그 취소의 결제 취소가 완료된 **뒤에** 생성된 확정 예약과 겹치는 부분만 합친 것.
3. **재판매 금액**: 겹치는 부분이 있는 확정 예약의 적용가 합계. 한 예약은 한 번만 센다.

예: 90분 예약 2건(13:00~14:30, 14:30~16:00)이 취소되고 60분 예약 3건(13·14·15시)이 들어오면 비었던 시간 180분, 다시 채운 시간 180분, 회수율 100%, 재판매 금액은 3건 합계. 건수(취소 2 : 재판매 3)로 비율을 내면 150% 가 되므로 건수는 내려주지 않는다.

### 초임박 판정

예약 테이블에 가격 구간 컬럼이 없어서 점유 시작 시각(`HOLD_EXPIRES_AT − 5분`)과 시작 시각의 차이가 3시간 이하이면 초임박으로 본다. 경계(정확히 3시간)는 초임박이다.

## 화면 연결 메모

- 오늘 예약 목록(타임라인)은 기존 `GET /api/v1/owner/reservations?from={오늘}&to={오늘}` 을 쓴다.
- 매장명·주소·대표 이미지는 기존 매장 정보 API를 쓴다.
