# 매장 대시보드·분석 API

매장 센터 운영 현황(`/owner`)의 **오늘** 탭과 **매출·예약 분석** 탭에 쓰는 지표. 매장은 로그인한 매장 계정으로 정해진다.

- 권한: 매장(OWNER). 그 외 역할은 403, 비로그인은 401
- 기준 시각: 서버 시각(한국 시간)
- 기간 지표는 **시술일(시작 날짜) 기준**이며 **확정(`CONFIRMED`)·취소 완료(`CANCELLED`)** 예약만 센다. 점유·만료·취소 처리 중은 제외
- 금액은 **예약 시점 적용가** 기준이다. 실제 방문 여부와 현장 할인·추가 결제는 반영되지 않는다
  - 화면 안내 문구: "예약 시점 가격 기준이며, 실제 방문 여부와 현장 할인·추가 결제에 따라 실제 매출과 다를 수 있어요."
- **이용 추정**: 확정 예약 중 종료 시각이 지났고 노쇼가 아닌 예약. **예정**: 확정 예약 중 아직 끝나지 않은 예약
- **노쇼**: 매장이 노쇼로 표시한 확정 예약. 이용 추정 금액·건수, 신규·재방문, 요일별·시간대·시술 집계에서 빠지고 `noShowCount`·`noShowRate` 로 따로 센다

| 메서드 | 경로 | 화면 |
| --- | --- | --- |
| GET | `/api/v1/owner/dashboard` | 오늘 탭 카드 |
| GET | `/api/v1/owner/dashboard/stats` | 매출·예약 분석 탭 |

## GET /api/v1/owner/dashboard

### 응답 예시

```json
{
  "success": true,
  "data": {
    "date": "2026-11-06",
    "today": {
      "confirmedCount": 4,
      "upcomingCount": 2,
      "closed": false,
      "availableMinutes": 240,
      "availableRanges": [
        { "startsAt": "2026-11-06T14:00:00+09:00", "endsAt": "2026-11-06T17:30:00+09:00" },
        { "startsAt": "2026-11-06T18:30:00+09:00", "endsAt": "2026-11-06T19:00:00+09:00" }
      ]
    },
    "tomorrowConfirmedCount": 3,
    "monthToDate": {
      "from": "2026-11-01",
      "to": "2026-11-06",
      "completedAmount": 820000,
      "previousFrom": "2026-10-01",
      "previousTo": "2026-10-06",
      "previousCompletedAmount": 740000,
      "changeRate": 10.8
    },
    "favoriteCustomerCount": 37
  }
}
```

| 필드 | 의미 |
| --- | --- |
| `today.confirmedCount` | 오늘 시작하는 확정 예약 |
| `today.upcomingCount` | 그중 아직 시작하지 않은 예약 |
| `today.closed` | 오늘 영업시간·열기로 열린 시간이 전혀 없음(휴무). `false` 인데 `availableRanges` 가 비어 있으면 남은 시간이 모두 찬 것 |
| `today.availableMinutes` | 지금 예약받을 수 있는 남은 빈 시간(분) 합계 |
| `today.availableRanges` | 남은 빈 시간대 `[startsAt, endsAt)`. 고객 예약 화면과 같은 기준(영업시간·열기·닫기·기존 예약·시작 20분 전 접수 마감). 30분 단위라 시술 소요 시간보다 짧은 틈도 포함 |
| `tomorrowConfirmedCount` | 내일 시작하는 확정 예약 |
| `monthToDate.completedAmount` | 이번 달 1일~오늘, 지금까지 끝난 확정 예약의 적용가 합계 |
| `monthToDate.previousCompletedAmount` | 지난달 1일~같은 날(지난달이 짧으면 말일)의 같은 금액 |
| `monthToDate.changeRate` | 증감률(%) 소수 첫째 자리. 지난달 금액이 0 이면 `null` |
| `favoriteCustomerCount` | 이 매장을 찜(업장 구독, 사용 중)한 고객 수. 지역·카테고리 알림 구독은 세지 않는다 |

## GET /api/v1/owner/dashboard/stats

- 파라미터: `from`, `to` (`yyyy-MM-dd`, 둘 다 포함), `all` (`true` 면 from·to 를 무시하고 **서비스 이용 시작일 ~ 오늘**)
  - 둘 다 생략: 이번 달 1일~말일
  - `from` 만 생략: `to` 가 속한 달 1일 ~ `to`
  - `to` 만 생략: `from` ~ `from` 이 속한 달 말일
- 서비스 이용 시작일(`serviceStartDate`): 매장 가입일과 첫 예약 시작일 중 이른 날. 화면에는 "서비스 이용 이후 전체"로 표시한다 (개업일이 아니다)
- 제한: `to` < `from` 이거나 기간이 3,660일을 넘으면 400 (`COMMON_400`)
- 추이 단위(`granularity`): 기간이 62일 이하면 `DAY`, 넘으면 `MONTH`

### 응답 예시

```json
{
  "success": true,
  "data": {
    "granularity": "MONTH",
    "serviceStartDate": "2025-11-01",
    "summary": { "from": "2025-11-07", "to": "2026-11-06", "completedCount": 384, "...": "기간 요약 필드" },
    "previous": null,
    "trend": [
      { "periodStart": "2025-11-07", "periodEnd": "2025-11-30", "completedCount": 11, "completedAmount": 330000,
        "upcomingCount": 0, "upcomingAmount": 0, "cancelledCount": 1 }
    ],
    "byWeekday": [ { "dayOfWeek": "MONDAY", "count": 31, "amount": 930000 } ],
    "heatmap": [ { "dayOfWeek": "SATURDAY", "hour": 14, "bookedMinutes": 1860 } ],
    "treatments": [ { "treatmentId": 201, "treatmentName": "젤네일 원컬러", "count": 300, "amount": 10200000, "share": 77.0 } ]
  }
}
```

| 필드 | 의미 | 화면 |
| --- | --- | --- |
| `summary` | 기간 요약 | 핵심 지표 카드 |
| `previous` | 바로 앞 같은 길이 기간의 요약. 그 기간이 서비스 이용 시작 전이면 `null` | 카드의 ▲▼ |
| `trend` | 기간별 이용 추정·예정 건수·금액, 취소 완료 건수(유형 무관). 빈 구간도 0 으로 채움. `MONTH` 의 첫·마지막 칸은 조회 기간에 걸친 날짜만 담는다 | 추이 막대 |
| `byWeekday` | 요일별 확정 예약(이용 추정 + 예정) 건수·금액. 월~일 7개 | 요일별 막대 |
| `heatmap` | 요일 × 시간대(정시~정시) 확정 예약 점유 시간(분). 예약 시간을 시간대별로 나눠 더한다 (13:30~15:00 → 13시 30분, 14시 60분). 0 인 칸은 없음 | 예약이 차 있는 시간 |
| `treatments` | 시술별 확정 예약 건수·금액과 금액 비중(%). 금액 → 건수 많은 순 | 시술별 비중 |

## 기간 요약 필드

| 필드 | 의미 | 화면 라벨 예시 |
| --- | --- | --- |
| `from`, `to` | 집계 기간 (둘 다 포함) | |
| `completedCount`, `completedAmount` | 이용 추정 건수·적용가 합계 | 이용 추정 금액 |
| `upcomingCount`, `upcomingAmount` | 예정 건수·적용가 합계 | 예정 예약 금액 |
| `averageAmount` | 객단가 = 이용 추정 금액 ÷ 건수 (원 단위 반올림). 이용이 없으면 `null` | 객단가 |
| `customerCancelCount` | 고객 사정 취소 = 고객 직접 취소 + 고객 요청 대리 취소 | 고객 취소 |
| `shopFaultCancelCount` | 매장 사유 취소 | 매장 사유 취소 |
| `customerCancelRate` | 고객 취소 ÷ (확정 + 고객 취소 + 매장 사유 취소) × 100. 대상이 없으면 `null` | 고객 취소율 |
| `nonRefundedDepositAmount` | 취소 완료 건의 (납부 예약금 − 환불액) 합계. 수수료·정산 전 금액이라 "번 돈"과 다르다 | 환불되지 않은 예약금 |
| `cancelledMinutes`, `rebookedMinutes`, `rebookRate` | 취소로 비었던 시간(분), 그중 다시 채운 시간(분), 회수율(%) | 취소 후 다시 채운 시간 |
| `rebookedBookingAmount` | 비었던 시간에 다시 들어온 확정 예약의 적용가 합계. 겹친 예약 전체 금액이라 손실 회수액과 같지 않다 | 재판매 관련 예약 금액 |
| `lastMinuteCount`, `lastMinuteAmount` | 시작 3시간 이내에 점유한 확정 예약 건수·금액 | 막판 예약 |
| `customerCount` | 기간 안에 이용한 고객 수 | 이용 고객 |
| `newCustomerCount`, `returningCustomerCount` | 그중 기간 끝 시점까지 이 매장 누적 이용이 1회인 고객 / 2회 이상인 고객 | 신규 / 재방문 |
| `revisitRate` | 재방문 ÷ 이용 고객 × 100. 이용 고객이 없으면 `null` | 재방문율 |
| `noShowCount` | 노쇼로 표시한 확정 예약 | 노쇼 |
| `noShowRate` | 노쇼 ÷ (이용 추정 + 노쇼) × 100. 대상이 없으면 `null` | 노쇼율 |

비율은 소수 첫째 자리이며, `null` 이면 화면에서 `-` 로 표시한다.

### 재판매·회수율 계산

1. **비었던 시간**: 기간 안 취소 완료 예약의 시간을 합친 것(겹치면 한 번만). 매장이 취소하며 그 시간을 닫은 건은 다시 팔 수 없으므로 뺀다.
2. **다시 채운 시간**: 그 취소의 결제 취소가 완료된 **뒤에** 생성된 같은 날 확정 예약과 겹치는 부분만 합친 것.
3. **재판매 관련 예약 금액**: 겹치는 부분이 있는 확정 예약의 적용가 합계. 한 예약은 한 번만 센다.

예: 90분 예약 2건(13:00~14:30, 14:30~16:00)이 취소되고 60분 예약 3건(13·14·15시)이 들어오면 비었던 시간 180분, 다시 채운 시간 180분, 회수율 100%.

### 초임박 판정

점유 시작 시각(`HOLD_EXPIRES_AT − 5분`)과 시작 시각의 차이가 3시간 이하이면 초임박으로 본다. 경계(정확히 3시간)는 초임박이다.

## 화면 연결 메모

- 오늘 예약 목록은 기존 `GET /api/v1/owner/reservations?from={오늘}&to={오늘}` 을 쓴다.
- 고객 배지·메모는 [매장 고객 관리 API](owner-customers.md)를 쓴다.
