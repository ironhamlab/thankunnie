# 사장님 메인 대시보드 API

사장님 센터 첫 화면(`/owner`)에 쓰는 요약 지표. 매장은 로그인한 사장님의 매장으로 정해진다.

## GET /api/v1/owner/dashboard

- 권한: 사장님(OWNER). 그 외 역할은 403, 비로그인은 401
- 기준 시각: 서버 시각(한국 시간)
- 월간 지표: **시술일(`STARTS_AT`) 기준** 이번 달 1일 00:00 ~ 다음 달 1일 00:00 (끝 제외)

### 응답 예시

```json
{
  "success": true,
  "data": {
    "date": "2026-11-02",
    "today": {
      "confirmedCount": 1,
      "upcomingCount": 1
    },
    "month": {
      "from": "2026-11-01",
      "to": "2026-11-30",
      "confirmedCount": 2,
      "expectedSales": 60000,
      "cancelledCount": 1,
      "customerCancelCount": 1,
      "shopFaultCancelCount": 0,
      "customerRequestCancelCount": 0,
      "cancelPendingCount": 0,
      "rebookedCount": 1,
      "rebookedSales": 15000
    }
  },
  "error": null,
  "timestamp": "2026-11-02T13:00:00+09:00"
}
```

### 필드

| 필드 | 의미 |
| --- | --- |
| `today.confirmedCount` | 오늘 시작하는 확정 예약 |
| `today.upcomingCount` | 그중 아직 시작하지 않은 예약 (시작 시각이 현재보다 뒤) |
| `month.confirmedCount` | 이번 달 확정 예약 |
| `month.expectedSales` | 확정 예약 적용가 합계. 현장 잔금을 포함한 예상 매출 |
| `month.cancelledCount` | 취소 완료(`CANCELLED`) 합계 = 아래 세 유형의 합 |
| `month.customerCancelCount` | 고객 직접 취소 |
| `month.shopFaultCancelCount` | 매장 사유 취소 |
| `month.customerRequestCancelCount` | 고객 요청 대리 취소 |
| `month.cancelPendingCount` | 취소 처리 중(`CANCEL_PENDING`) |
| `month.rebookedCount` | 취소로 다시 열린 시간에 들어온 확정 예약 |
| `month.rebookedSales` | 그 예약들의 적용가 합계 |

### 재판매 판정

확정 예약 `r` 이 아래를 모두 만족하면 재판매로 센다.

- 같은 매장의 취소 완료 예약 `rc` 와 시간이 겹친다 (`rc.STARTS_AT < r.ENDS_AT` 이고 `r.STARTS_AT < rc.ENDS_AT`)
- `r` 이 그 취소의 결제 취소 완료 시각(`RESERVATION_CANCEL.COMPLETED_AT`) 이후에 생성되었다

예약 하나가 여러 취소 시간과 겹쳐도 1건으로 센다.

### 화면 연결 메모

- 오늘 예약 목록(타임라인)은 이 API에 넣지 않았다. 기존 `GET /api/v1/owner/reservations?from={오늘}&to={오늘}` 을 쓴다.
- 매장명·주소·대표 이미지는 기존 매장 정보 API를 쓴다.

---

## GET /api/v1/owner/dashboard/stats

통계 화면(차트)용 기간 집계.

- 권한: 사장님(OWNER)
- 파라미터: `from`, `to` (`yyyy-MM-dd`, 시술일 기준, 둘 다 포함). 생략하면 `to` 는 오늘, `from` 은 `to` 포함 30일 전
- 제한: `to` < `from` 이거나 기간이 92일을 넘으면 400 (`COMMON_400`)
- 대상: 확정(`CONFIRMED`)·취소 완료(`CANCELLED`) 예약만 센다. 점유·만료·취소 처리 중은 제외

### 응답 예시

```json
{
  "success": true,
  "data": {
    "from": "2026-11-01",
    "to": "2026-11-02",
    "summary": {
      "confirmedCount": 2,
      "sales": 60000,
      "cancelledCount": 1,
      "rebookedCount": 1,
      "rebookedSales": 15000,
      "lastMinuteCount": 0,
      "lastMinuteSales": 0
    },
    "daily": [
      { "date": "2026-11-01", "confirmedCount": 0, "sales": 0, "cancelledCount": 0 },
      { "date": "2026-11-02", "confirmedCount": 1, "sales": 30000, "cancelledCount": 0 }
    ],
    "treatments": [
      { "treatmentId": 1, "treatmentName": "젤네일 원컬러", "confirmedCount": 2, "sales": 60000, "cancelledCount": 0 }
    ],
    "timeSlots": [
      { "dayOfWeek": "MONDAY", "hour": 14, "confirmedCount": 1 }
    ]
  }
}
```

### 필드

| 필드 | 의미 | 차트 예시 |
| --- | --- | --- |
| `summary` | 기간 합계. `sales` 는 확정 적용가 합계(예상 매출) | 상단 숫자 카드 |
| `summary.rebookedCount`·`rebookedSales` | 취소로 다시 열린 시간에 들어온 확정 예약 (판정은 위 대시보드와 같음) | "취소 후 재판매" 카드 |
| `summary.lastMinuteCount`·`lastMinuteSales` | 시작 3시간 이내에 점유한 확정 예약 (초임박가 판매) | "땡처리 매출" 카드 |
| `daily` | 날짜별 확정 건수·매출·취소 건수. 예약 없는 날도 0 으로 채워 `from`~`to` 를 모두 담음 | 라인 차트 |
| `treatments` | 시술별 확정 건수·매출·취소 건수. 매출 → 확정 건수 많은 순 | 막대 차트 |
| `timeSlots` | 요일(`MONDAY`~`SUNDAY`)·시작 시각(시)별 확정 건수. 0 인 칸은 없음 | 요일 × 시간 히트맵 |

초임박 판정: 예약 테이블에 가격 구간 컬럼이 없어서 점유 시작 시각(`HOLD_EXPIRES_AT − 5분`)과 시작 시각의 차이가 3시간 이하이면 초임박으로 본다. 경계(정확히 3시간)는 초임박이다.

비율(취소율, 재판매율 등)은 화면에서 위 건수로 계산한다.
