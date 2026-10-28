# C-01 빈자리 탐색 API 계약

상태: **C 결정 완료 · 구현 대기** · 작성일: 2026-10-28

고객이 빈자리를 찾아보는(목록·지도) 탐색 기능의 요청·응답 형식을 정한다. A-01·A-02에서 제안한 `/api/v1/items` 임시 계약을 실제 스키마(`docs/erd/01_schema_ddl.sql`)와 B의 빈자리 공통 규칙(`domain/vacancy`)에 맞춰 구체화했다. 결정 내용은 6장에 정리했다.

## 1. 담당 구분

| 기능 | 담당 | 비고 |
| --- | --- | --- |
| 시간대 열기·닫기(빈자리 등록), 영업시간 | B | `domain/schedule` |
| 가용 시간·가격 구간 계산 규칙, 가용 시간 조회 API | B | `domain/vacancy` (B-05, B-06) |
| **빈자리 탐색 목록 API** (홈·검색·지도 공용) | **C** | 신규 `domain/explore`. B 규칙을 읽기 전용으로 재사용하고 B 코드는 수정하지 않는다 |
| **메인·검색·지도 화면의 실제 API 연결과 마무리** | **C** | A가 만든 화면에 연결 |
| 상세 화면 | A | C는 매장 위치 지도만 추가한다 (4장) |
| 예약 점유·결제 | D | 상세의 "예약하기" 이후 |

- B 규칙 변경(예: 상세 시간 선택을 OPEN 시각으로 제한)이 필요하면 C가 직접 고치지 않고 B 담당에게 요청한다.
- 메인 전용 API는 만들지 않는다. 메인의 "오늘", "마감 임박" 영역도 C-01을 파라미터 조합으로 호출한다 (A-01 원칙 유지).

## 2. 빈자리 한 건의 정의

**빈자리 = 매장의 시술 1개 × 예약 가능한 시작 시각 1개**

- 대상: `SHOP.STATUS = ACTIVE` 매장의 `TREATMENT.STATUS = ACTIVE` 시술
- 시작 시각: 사장님이 연 시간대(`SHOP_SCHEDULE_EXCEPTION.EXCEPTION_TYPE = OPEN`) 안에서 시작하는 30분 단위 시각. 영업시간 안의 일반 빈 시간은 목록에 넣지 않는다
- 예약 가능 판단은 B-06과 같은 규칙을 쓴다 (`AvailabilityCalculator`, `SalePolicy.isBookable`)
  - 시술 구간 `[시작, 시작 + 소요 시간)`이 열린 구간 안에 모두 들어가고, 닫기·잡혀 있는 예약과 겹치지 않는다
  - 시작 20분 전까지, 오늘 포함 60일 이내
- 가격은 조회 시점 기준 구간(`PriceTier`)으로 `PriceCalculator.quote`가 계산한다. **화면 가격은 안내용**이며, 예약 클릭 시 서버가 다시 계산한다

목록에는 지금 예약할 수 있는 빈자리만 내려준다. 따라서 A-02의 상태값(`HELD`, `BOOKED`, `SOLD_OUT` 등)은 목록 응답에 두지 않는다.

## 3. C-01 빈자리 목록 조회

| 항목 | 값 |
| --- | --- |
| Method | GET |
| Path | `/api/v1/explore/vacancies` |
| 인증 | 불필요 (비로그인 조회 가능) |
| 응답 | `ApiResponse<VacancyPageResponse>` |

### 요청 파라미터

모두 선택값이며 AND로 결합한다.

| 이름 | 타입 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `startDate` | `yyyy-MM-dd` | 오늘(KST) | 시작 날짜 (포함) |
| `endDate` | `yyyy-MM-dd` | `startDate` + 6일 | 종료 날짜 (포함). `startDate <= endDate`, 최대 60일 이내 |
| `regionCode` | string, 여러 개 (최대 10개) | 전체 | `SHOP.REGION_CODE`(시군구 5자리). `regionCode=11680&regionCode=11650`처럼 반복. 화면 지역 묶음과의 대응은 6장 |
| `categoryId` | number | 전체 | `TREATMENT.CATEGORY_ID` |
| `timeOfDay` | `MORNING`·`AFTERNOON`·`EVENING` | 전체 | 시작 시각 기준 09~12시 / 12~18시 / 18시 이후 |
| `closingWithinHours` | number (1~72) | 없음 | 예약 마감(시작 20분 전)이 지금부터 N시간 안인 빈자리만. 메인 "마감 임박" 영역용 |
| `priceTier` | `IMMINENT`·`LAST_MINUTE` | 전체 | 해당 가격 구간 이상만. 홈 "임박 특가만 보기"는 `IMMINENT` |
| `keyword` | string (최대 50자) | 없음 | 매장명·시술명 부분 일치 |
| `minLat`, `maxLat`, `minLng`, `maxLng` | number | 없음 | 지도 화면 범위. 4개를 함께 보내야 하며 `min < max`. 매장 좌표가 범위 안인 빈자리만 |
| `sort` | `START_ASC`·`DISCOUNT_DESC`·`PRICE_ASC` | `START_ASC` | 빠른 시작순 / 할인율 높은 순 / 판매가 낮은 순 |
| `page` | number (0 이상) | 0 | |
| `size` | number (1~50) | 20 | 지도 화면은 50 |

- 같은 정렬값이면 `startsAt ASC, treatmentId ASC` 순서를 보장한다.
- 홈 정렬 "임박 빈자리 우선순"은 `START_ASC`와 같다 (시작이 빠를수록 임박).
- 지원하지 않는 파라미터 값, 날짜 범위 오류, `size` 초과는 400 (`COMMON_400`).

### 메인·홈 화면 호출 예시

```http
GET /api/v1/explore/vacancies?startDate=2026-10-29&endDate=2026-10-29&sort=START_ASC&size=6
GET /api/v1/explore/vacancies?closingWithinHours=24&sort=START_ASC&size=6
GET /api/v1/explore/vacancies?startDate=2026-10-29&regionCode=11680&regionCode=11650&categoryId=1&timeOfDay=AFTERNOON&sort=DISCOUNT_DESC
GET /api/v1/explore/vacancies?startDate=2026-10-29&minLat=37.48&maxLat=37.52&minLng=127.00&maxLng=127.06&size=50
```

### 지도 화면 호출 방식

- 지도를 처음 열 때와 이동·확대·축소가 끝났을 때(카카오맵 `idle` 이벤트) 현재 화면 범위로 다시 조회한다.
- 연속 이동 중 요청이 몰리지 않도록 300ms 정도 기다렸다가 마지막 범위로만 조회하고, 늦게 도착한 이전 응답은 버린다.
- 지도 범위 조회에서는 범위 맞추기(`setBounds`)를 하지 않는다. 사용자가 옮긴 화면이 다시 움직이지 않게 하기 위함이다.
- 화면 안 빈자리가 `size`보다 많으면 `hasNext=true`이며, 지도에는 "확대하면 더 보여요" 안내를 표시한다.

### 성공 응답 예시

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "vacancyId": "3-202610291530",
        "shopId": 1,
        "shopName": "언니네 네일",
        "regionCode": "11680",
        "address": "서울특별시 강남구 테헤란로 1",
        "latitude": 37.4979,
        "longitude": 127.0276,
        "treatmentId": 3,
        "treatmentName": "젤 네일 원컬러 + 케어",
        "categoryId": 1,
        "categoryName": "네일",
        "treatmentType": "NORMAL",
        "durationMinutes": 60,
        "imageUrl": "/api/v1/shops/1/treatments/3/images/abc.jpg",
        "startsAt": "2026-10-29T15:30:00+09:00",
        "endsAt": "2026-10-29T16:30:00+09:00",
        "bookingClosesAt": "2026-10-29T15:10:00+09:00",
        "priceTier": "LAST_MINUTE",
        "regularPrice": 50000,
        "salePrice": 32500,
        "discountRate": 35,
        "depositAmount": 20000
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  },
  "error": null,
  "timestamp": "2026-10-29T13:00:00+09:00"
}
```

### 응답 필드

| 필드 | 타입 | 설명 | 화면 사용처 |
| --- | --- | --- | --- |
| `vacancyId` | string | `{treatmentId}-{yyyyMMddHHmm}`. 목록 key용 | 카드 key |
| `shopId`, `shopName` | number, string | 매장 | 카드 |
| `regionCode`, `address` | string | 매장 주소 | 카드 위치 문구 |
| `latitude`, `longitude` | number | 매장 좌표 (`SHOP.LATITUDE/LONGITUDE`) | 카카오맵 마커 |
| `treatmentId`, `treatmentName` | number, string | 시술 | 카드 제목 |
| `categoryId`, `categoryName` | number, string | 시술 카테고리 | 카드 배지 |
| `treatmentType` | `NORMAL`·`MODEL` | 일반 / 모델 시술 | 모델 배지 |
| `durationMinutes` | number | 소요 시간(분) | "60분 소요" |
| `imageUrl` | string, null 가능 | 시술 이미지(`TREATMENT.IMAGE_URL`), 없으면 매장 이미지(`SHOP.IMAGE_URL`), 둘 다 없으면 null | 카드 이미지 (null이면 기본 이미지) |
| `startsAt`, `endsAt` | ISO-8601 (+09:00) | 시술 시작·종료 (종료 미포함) | "오늘 15:30" |
| `bookingClosesAt` | ISO-8601 (+09:00) | 예약 클릭 마감 = 시작 20분 전 | "40분 뒤 마감" |
| `priceTier` | `NORMAL`·`IMMINENT`·`LAST_MINUTE` | 조회 시점 가격 구간 | 임박·초임박 특가 배지 |
| `regularPrice` | number | 정가 | 취소선 가격 |
| `salePrice` | number | 판매가 (구간 적용) | 강조 가격 |
| `discountRate` | number | 할인율(%), 버림 | "35%" |
| `depositAmount` | number | 예약금 = min(판매가, 20,000) | 상세·예약 안내 |

시각은 모두 `+09:00` 오프셋을 포함한다. 금액은 원 단위 정수다.

## 4. 상세 화면 연계 (A 담당 화면에 C 지도 추가)

상세 화면(`pages/service/ServiceDetailPage.jsx`)과 상세 데이터 조회는 A 담당이다. C는 이 화면에 **매장 위치 지도**만 추가한다.

- 공통 지도 컴포넌트 `components/map/KakaoMap`에 매장 좌표 마커 1개를 넘겨 표시한다. 지도 아래에 매장 주소를 함께 보여준다.
- 지도에 필요한 값은 매장의 `latitude`, `longitude`, `address`(+`addressDetail`), `shopName`이다. 상세 데이터를 어떤 API로 받든 이 네 값이 응답에 들어 있어야 한다 (A 담당에게 요청할 사항).
- 실제 API 연결 전까지는 데모 데이터(`utils/demoData.js`의 `lat`, `lng`)로 표시한다.
- 카카오맵 키가 없거나 SDK를 불러오지 못하면 지도 영역 대신 주소 문구만 보여준다.
- 목록 카드의 상세 이동 경로·전달 값은 A 담당의 상세 화면 계약을 따른다. 목록 응답의 `treatmentId`, `startsAt`, `shopId`로 이동 정보를 만들 수 있다.

## 5. 기존 문서·화면과의 대응

| A-01·A-02 / 데모 화면 | 이 계약 |
| --- | --- |
| `GET /api/v1/items` | `GET /api/v1/explore/vacancies` |
| `id` (양의 정수) | `vacancyId` (문자열, 시술 ID + 시작 시각) |
| `title` | `treatmentName` |
| `applicationClosesAt` | `bookingClosesAt` |
| `availableOnly=true` | 항상 예약 가능한 것만 반환하므로 파라미터 없음 |
| `price` / `originalPrice` | `salePrice` / `regularPrice` |
| `status` (AVAILABLE 등) | 목록에는 없음 (예약 가능한 것만 반환) |
| 데모 `region` ('강남/서초' 등) | `regionCode` 여러 개로 변환 (6장) |
| 데모 `urgency` ('초임박 특가' 등) | `priceTier` |
| 데모 `lat`, `lng` | `latitude`, `longitude` |

## 6. 결정 사항 (2026-10-28)

1. **빈자리의 범위**: 사장님이 연 시간대(OPEN) 안의 시각만 목록에 노출한다. 서비스 취지(사장님이 등록한 타임세일 빈자리), 알림 이벤트(`NOTIFICATION_EVENT`가 OPEN 행 기준)와 맞고 결과 수가 과도하게 늘지 않는다.
   - 남은 확인: 상세의 시간 선택(`available-times`, B)은 영업시간까지 포함한다. 상세에서 목록에 없던 시간을 고를 수 있게 둘지는 A·B 담당과 정한다.
2. **경로·패키지**: API는 `/api/v1/explore/vacancies`, 백엔드 코드는 `domain/explore`. 경로를 화면(메인·index) 이름이 아니라 기능(탐색)과 데이터(빈자리) 이름으로 지어, B의 빈자리 관리 API와 섞이지 않게 한다. 메인·검색·지도 화면이 모두 같은 API를 조건만 바꿔 호출한다. 프런트의 메인 화면 경로는 기존대로 `/`(`ROUTES.HOME`)이다.
3. **지역 필터**: API는 `regionCode`(시군구 5자리)를 여러 개 받고, 화면의 지역 묶음은 프런트 상수(`constants/regions.js`)로 코드 목록에 대응시킨다. 지역 묶음을 바꿔도 서버 수정이 필요 없다.

   | 화면 지역 묶음 | `regionCode` |
   | --- | --- |
   | 강남/서초 | `11680` 강남구, `11650` 서초구 |
   | 홍대/연남 | `11440` 마포구 |
   | 성수/건대 | `11200` 성동구, `11215` 광진구 |
   | 잠실/송파 | `11710` 송파구 |
   | 판교/분당 | `41135` 성남시 분당구 |

   매장의 `REGION_CODE`는 매장 등록(A-03) 때 카카오 주소 검색 결과의 법정동 코드 앞 5자리로 채운다. 알림의 지역 구독(`NOTIFICATION_SUBSCRIPTION.REGION_CODE`)도 같은 코드를 쓴다.
4. **지도 범위 조회**: 지도를 움직일 때마다 화면 범위 안의 빈자리만 다시 불러온다 (`minLat`·`maxLat`·`minLng`·`maxLng`, 3장 "지도 화면 호출 방식").
5. **이미지 우선순위**: 시술 이미지 → 매장 이미지 → null.
