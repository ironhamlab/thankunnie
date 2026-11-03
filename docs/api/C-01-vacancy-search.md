# C 빈자리 탐색·지도 API 계약

상태: **C 결정 완료 · 구현 대기** · 작성일: 2026-10-28

고객이 빈자리를 찾아보고(목록·지도) 고르는(빈자리 상세) 탐색 기능의 요청·응답 형식과 지도 메인 화면 흐름을 정한다. A-01·A-02에서 제안한 `/api/v1/items` 임시 계약을 실제 스키마(`docs/erd/01_schema_ddl.sql`)와 B의 빈자리 공통 규칙(`domain/vacancy`)에 맞춰 구체화했다.

| API | 용도 | 장 |
| --- | --- | --- |
| C-01 `GET /api/v1/explore/vacancies` | 빈자리 목록 (홈·검색·지도 패널 공용) | 3장 |
| C-02 `GET /api/v1/explore/shops` | 지도 마커용 매장별 요약 (위치 + 최저가) | 4장 |
| C-03 `GET /api/v1/explore/vacancies/{treatmentId}` | 빈자리 상세 (선택한 시각의 가격·예약 가능 여부) | 5장 |

## 1. 담당 구분

| 기능 | 담당 | 비고 |
| --- | --- | --- |
| 시간대 열기·닫기(빈자리 등록), 영업시간 | B | `domain/schedule` |
| 가용 시간·가격 구간 계산 규칙, 가용 시간 조회 API | B | `domain/vacancy` (B-05, B-06) |
| **빈자리 탐색 API (C-01·C-02·C-03)** | **C** | 신규 `domain/explore`. B 규칙을 읽기 전용으로 재사용하고 B 코드는 수정하지 않는다 |
| **지도 메인 화면** (전체 매장 위치·가격 마커 → 매장 빈자리 → 빈자리 상세) | **C** | 6장 |
| **메인·검색 화면의 실제 API 연결과 마무리** | **C** | A가 만든 화면에 연결 |
| 매장 상세 화면 | A | A 구현·연결이 끝난 뒤 C가 매장 위치 지도를 추가한다 (7장) |
| 예약 점유·결제 | D | 빈자리 상세의 "예약하기" 이후 |

- B 규칙 변경(예: 상세 시간 선택을 OPEN 시각으로 제한)이 필요하면 C가 직접 고치지 않고 B 담당에게 요청한다.
- 메인 전용 API는 만들지 않는다. 메인의 "오늘", "마감 임박" 영역도 C-01을 파라미터 조합으로 호출한다 (A-01 원칙 유지).
  - 예외: 메인 임박 카드는 OPEN이 없어도 노출하고 매장당 1건만 보여줘야 해서 전용 API를 둔다 ([4-1장](#4-1-메인-임박-빈자리-unnie-157)).

## 2. 빈자리 한 건의 정의

**빈자리 = 매장의 시술 1개 × 예약 가능한 시작 시각 1개**

- 대상: `SHOP.STATUS = ACTIVE` 매장의 `TREATMENT.STATUS = ACTIVE` 시술
- 시작 시각: 매장이 연 시간대(`SHOP_SCHEDULE_EXCEPTION.EXCEPTION_TYPE = OPEN`) 안에서 시작하는 30분 단위 시각. 영업시간 안의 일반 빈 시간은 목록에 넣지 않는다
  - 예외 (UNNIE-165, 타임세일): `priceTier=LAST_MINUTE`이면 메인 임박 빈자리(4-1장)처럼 OPEN 없이 영업시간 안 빈 시각을 찾는다.
    - 범위: 시작 3시간 이내(초임박 구간, 정확히 3시간 전 포함). 예약 클릭 마감(시작 20분 전)은 B의 가용 시간 계산을 그대로 따른다. 날짜를 생략하면 자정을 넘은 새벽 시각도 본다.
    - 할인 조건: 초임박가(`LAST_MINUTE_PRICE`)가 일반 판매가(`PRICE`)보다 싼 시술만. 8~3시간 전(임박) 구간은 판매가와 같아 타임세일에 넣지 않는다.
    - 지역·분야·시간대·검색어·지도 범위·시술 유형(초보 시술) 조건은 똑같이 적용한다. `IMMINENT`나 조건 없는 조회는 OPEN 기준 그대로다.
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
| `regionCode` | string, 여러 개 (최대 10개) | 전체 | `SHOP.REGION_CODE`(시군구 5자리). `regionCode=11680&regionCode=11650`처럼 반복. 화면 지역 묶음과의 대응은 9장 |
| `categoryId` | number | 전체 | `SHOP.CATEGORY_ID` |
| `shopId` | number | 전체 | 한 매장의 빈자리만. 지도에서 매장 마커를 눌렀을 때 사용 |
| `treatmentType` | `NORMAL`·`MODEL` | 전체 | 해당 시술 유형만. 초보 시술 모집 목록에서 `MODEL` 로 사용 |
| `timeOfDay` | `MORNING`·`AFTERNOON`·`EVENING` | 전체 | 시작 시각 기준 09~12시 / 12~18시 / 18시 이후 |
| `closingWithinHours` | number (1~72) | 없음 | 예약 마감(시작 20분 전)이 지금부터 N시간 안인 빈자리만. 메인 "마감 임박" 영역용 |
| `priceTier` | `IMMINENT`·`LAST_MINUTE` | 전체 | 해당 가격 구간 이상만. 타임세일(`/?sale=1`·`/map?sale=1`·홈 지도 "타임세일만")은 `LAST_MINUTE`. `LAST_MINUTE`이면 OPEN 대신 영업시간 기준으로 찾고 초임박가가 판매가보다 싼 시술만 낸다 (2장 예외) |
| `keyword` | string (최대 50자) | 없음 | 매장명·주소(지역)·시술명 부분 일치. 예: `강서구`, `마곡` |
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
| `categoryId`, `categoryName` | number, string | 매장 카테고리 | 카드 배지 |
| `treatmentType` | `NORMAL`·`MODEL` | 일반 / 초보 시술 | 초보 시술 배지 |
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

## 4. C-02 지도 매장 요약

| 항목 | 값 |
| --- | --- |
| Method | GET |
| Path | `/api/v1/explore/shops` |
| 인증 | 불필요 |
| 응답 | `ApiResponse<ExploreShopMapResponse>` |

한 매장에 빈자리가 여러 개여도 좌표가 같아 마커가 겹치므로, 지도에는 **매장당 마커 1개**를 띄우고 그 매장 빈자리의 최저 판매가를 표시한다.

- 요청 파라미터는 C-01과 같다(`startDate`·`endDate`·`regionCode`·`categoryId`·`treatmentType`·`timeOfDay`·`closingWithinHours`·`priceTier`·`keyword`·지도 범위). `shopId`·`sort`·`page`·`size`는 받지 않는다.
- 조건에 맞는 빈자리가 1건 이상인 매장만 돌려준다. 순서는 가장 빠른 시작 시각, 매장 ID 순이다.
- 매장 상세는 일반 시술만, 초보 시술 모집 상세는 초보 시술만 보여주므로 `vacancyCount`·`minSalePrice`·`maxDiscountRate`·`earliestStartsAt`은 카드가 연결되는 쪽 빈자리로 계산한다. 일반 빈자리가 있으면 일반 빈자리, 없으면(`modelOnly=true`) 초보 시술 빈자리다.
- 한 번에 최대 200개 매장을 돌려주고, 더 있으면 `hasMore=true`다. 지도에는 "확대하면 더 보여요"를 표시한다.

```json
{
  "success": true,
  "data": {
    "shops": [
      {
        "shopId": 1,
        "shopName": "언니네 네일",
        "regionCode": "11680",
        "address": "서울특별시 강남구 테헤란로 1",
        "latitude": 37.4979,
        "longitude": 127.0276,
        "imageUrl": null,
        "vacancyCount": 7,
        "minSalePrice": 18000,
        "maxDiscountRate": 14,
        "earliestStartsAt": "2026-11-06T20:00:00+09:00",
        "modelOnly": false,
        "modelVacancyCount": 0,
        "modelMinSalePrice": null
      }
    ],
    "hasMore": false
  },
  "error": null,
  "timestamp": "2026-11-06T10:00:00+09:00"
}
```

| 필드 | 설명 | 화면 사용처 |
| --- | --- | --- |
| `shopId`, `shopName`, `address`, `regionCode` | 매장 | 마커 선택 시 패널 제목 |
| `latitude`, `longitude` | 매장 좌표 | 마커 위치 |
| `imageUrl` | 매장 이미지, 없으면 null | 패널 썸네일 |
| `vacancyCount` | 조건에 맞는 빈자리 수 | "빈자리 7개" |
| `minSalePrice` | 빈자리 중 최저 판매가 | 마커 라벨 "18,000원~" |
| `maxDiscountRate` | 빈자리 중 최대 할인율 | 마커 강조(할인 배지) |
| `earliestStartsAt` | 가장 빠른 시작 시각 (+09:00) | "오늘 20:00부터" |
| `modelOnly` | 조건에 맞는 빈자리가 모두 MODEL 시술인지 | true 면 매장 카드·"매장 보기"가 초보 시술 모집 상세(`/shops/{shopId}/models`)로 연결 |
| `modelVacancyCount`, `modelMinSalePrice` | MODEL 빈자리 수·최저 판매가 (없으면 0·null) | 일반·초보 시술이 섞인 매장의 "초보 시술 모집 빈자리 N개 · 가격~" 링크 |

## 4-1. 메인 임박 빈자리 (UNNIE-157)

`GET /api/v1/explore/vacancies/imminent` (비로그인 조회 가능, 파라미터 없음)

메인 화면 "오늘 마감 임박" 캐러셀 전용이다.

- **매장이 시간대를 열지 않아도 노출한다.** 다른 탐색 API와 달리 OPEN 시간대가 아니라 영업시간 기준으로 빈 시각을 찾는다.
  빈 시각 판단은 B의 `AvailabilityService.getAvailableStartTimes`(영업시간 + 열기 − 닫기 − 예약, 시작 20분 전 마감)를 그대로 쓴다.
  매장 상세의 시간 선택(`available-times`)·예약 생성과 같은 기준이라, 카드를 눌러 들어간 매장 상세에도 같은 시각이 있다.
- 대상: `SHOP.STATUS = ACTIVE` 매장의 `ACTIVE` **일반(NORMAL) 시술**. 초보 시술(MODEL)은 넣지 않는다.
- 범위: 지금부터 8시간 안에 시작하는 시각 (`BookingPolicy.IMMINENT_FROM`, 시작 시각 < 지금 + 8시간). 자정을 넘기면 다음 날 새벽 시각도 본다.
- **매장당 1건**: 그 매장에서 가장 빠른 시각. 같은 시각에 시술이 여러 개면 할인율이 높은 것, 그다음 시술 ID가 작은 것.
- 정렬: 시작 시각 빠른 순 → 할인율 높은 순 → 시술 ID. 최대 10건.

응답 `data`: `ExploreVacancyResponse[]` (C-01 목록의 `content` 한 건과 같은 모양, 페이지 정보 없음)

## 4-2. 전체 매장 목록

| 항목 | 값 |
| --- | --- |
| Method | GET |
| Path | `/api/v1/explore/shops/all` |
| 인증 | 불필요 |
| 응답 | `ApiResponse<List<ExploreShopListResponse>>` |

노출 중(`SHOP.STATUS = ACTIVE`)인 매장을 빈자리 유무와 상관없이 매장 ID 순으로 돌려준다. 일반 예약하기 목록(`/?view=list&date=all`)에서 빈자리가 없는 매장까지 보여줄 때 쓴다.

| 파라미터 | 값 | 기본 | 설명 |
| --- | --- | --- | --- |
| `categoryId` | 분야 ID | 전체 | 해당 분야 매장만 |
| `keyword` | 50자 이하 | 없음 | 매장명·주소(지역)·판매 중인 시술명 부분 일치. 대소문자 구분 없음. 50자를 넘으면 `COMMON_400` |

응답 필드는 `shopId`, `shopName`, `categoryId`, `categoryName`, `regionCode`, `address`, `latitude`, `longitude`, `imageUrl`(없으면 null)이다. 페이지로 나누지 않는다.

## 5. C-03 빈자리 상세

| 항목 | 값 |
| --- | --- |
| Method | GET |
| Path | `/api/v1/explore/vacancies/{treatmentId}?startsAt=2026-11-06T20:00:00` |
| 인증 | 불필요 |
| 응답 | `ApiResponse<ExploreVacancyDetailResponse>` |

- 지도 메인 화면에서 빈자리를 고르면 호출한다. `vacancyId`(`{treatmentId}-{yyyyMMddHHmm}`)에서 `treatmentId`와 `startsAt`을 얻는다.
- C-01 응답 필드 전체에 아래 필드를 더한다. 가격·예약 가능 여부는 **조회 시점에 다시 계산**한다 (목록 값을 넘겨받지 않는다).

| 추가 필드 | 타입 | 설명 |
| --- | --- | --- |
| `bookable` | boolean | 지금 이 시각을 예약할 수 있는지 |
| `notBookableReason` | string, null 가능 | `BOOKING_CLOSED`(마감), `NOT_AVAILABLE`(다른 예약·닫힘 등으로 시작 불가), `HELD`(다른 고객이 결제 중. 화면은 예약하기 대신 대기하기로 예약 대기열에 선다). `bookable=true`면 null |
| `treatmentDescription` | string, null 가능 | 시술 설명 |
| `modelCondition` | string, null 가능 | 초보 시술 조건 (`MODEL`일 때) |
| `preparationNotes`, `photoNotice` | string, null 가능 | 준비 사항, 촬영 안내 |
| `addressDetail`, `phone` | string | 상세 주소, 매장 연락처 |
| `shopDescription`, `shopNotice` | string, null 가능 | 매장 소개, 공지 |
| `balanceAmount` | number | 현장 결제 잔금 = 판매가 − 예약금 |

- 다른 시각을 고르려면 `GET /api/v1/available-times?shopId={shopId}&date={date}&durationMinutes={durationMinutes}`(B)로 시작 가능 시각을 받고, 고른 시각으로 C-03을 다시 호출한다. 응답의 `extendedStartTimes`는 그중 영업시간 밖 연장 영업 덕분에 가능한 시각이다(필드 설명은 A-F1 문서).
- 이미 지났거나 마감된 시각이라도 200으로 응답하고 `bookable=false`로 알린다. 화면은 "예약 마감" 안내와 다른 시간 선택을 보여준다.

| HTTP | 코드 | 상황 |
| --- | --- | --- |
| 400 | `COMMON_400` | `startsAt` 누락·형식 오류 |
| 400 | `VACANCY_004` | `startsAt`이 30분 단위가 아님 |
| 400 | `VACANCY_003` | 오늘부터 60일 범위 밖 |
| 404 | `TREATMENT_001` | 시술이 없거나 비활성, 매장이 비활성 |

## 6. 지도 메인 화면 (C, `/map`)

전체 매장의 위치와 가격을 지도에서 보고, 매장 → 빈자리 → 빈자리 상세까지 한 화면에서 이어 보는 C 담당 화면이다. 공통 지도 컴포넌트 `components/map/KakaoMap`(UNNIE-36)을 쓴다.

```text
지도 열기 / 이동·확대·축소 끝(idle)
  → C-02 매장 요약 (현재 화면 범위 + 필터)  → 매장마다 "최저가~" 마커
마커 클릭
  → C-01 빈자리 목록 (shopId + 같은 필터, START_ASC)  → 옆 패널(모바일은 아래 시트)에 매장 빈자리 카드
빈자리 카드 클릭
  → C-03 빈자리 상세  → 가격·예약금·예약 가능 여부·시술/매장 안내, 다른 시간 선택(available-times)
"예약하기"
  → D 예약 점유 화면으로 이동 (treatmentId, startsAt 전달)
```

- 필터(날짜·지역·카테고리·시간대·임박 특가·검색어)는 화면 위쪽에 두고, 바꾸면 C-02를 다시 조회한다. 열려 있던 매장 패널은 닫는다.
- 지도 범위 조회: 이동이 끝나고 300ms 기다린 뒤 마지막 범위로만 조회하고, 늦게 도착한 이전 응답은 버린다. 범위 조회 결과로 지도 범위를 다시 맞추지(`setBounds`) 않는다.
- 처음 열 때 화면 범위는 브라우저 위치 권한 없이 기본 위치(강남역)에서 시작한다. 내 위치 이동은 추후 기능이다.
- 카카오맵 키가 없거나 SDK를 불러오지 못하면 지도 대신 C-01 목록을 보여준다.

## 7. 매장 상세 화면 위치 지도 (A 담당 화면에 C 추가, UNNIE-141 완료)

매장 상세 화면과 상세 데이터 조회는 A 담당이다. C가 **매장 위치 지도**를 상세정보 탭에 추가했다.

- 위치: 매장 상세(`/shops/:shopId`) "상세정보" 탭의 주소·연락처·영업시간 아래 (`pages/shop/components/ShopLocation`, `ShopInfoPanel`에 한 줄 추가).
- `KakaoMap`에 매장 이름 라벨 마커 1개를 넘기고 그 위치를 가운데로, 확대 단계 3으로 보여준다.
- 지도 아래 링크 두 개: "카카오맵 길찾기"(`https://map.kakao.com/link/to/{도로명 주소},{위도},{경도}`, 도착지로 길찾기. 모바일 카카오맵은 도착지를 이름으로 다시 검색해 카카오에 없는 매장명은 찾지 못하므로 주소를 쓴다), "지도 크게 보기"(`/link/map/...`). 새 탭으로 연다.
- 필요한 값(`latitude`, `longitude`, `shopName`)은 매장 상세 응답(`GET /api/v1/explore/shops/{shopId}`)에 이미 있다. 주소는 위 상세정보 줄에 있다.
- 카카오맵 키가 없거나 SDK를 불러오지 못하면 지도는 빼고 링크만 보여준다.

## 8. 기존 문서·화면과의 대응

| A-01·A-02 / 데모 화면 | 이 계약 |
| --- | --- |
| `GET /api/v1/items` | `GET /api/v1/explore/vacancies` |
| `id` (양의 정수) | `vacancyId` (문자열, 시술 ID + 시작 시각) |
| `title` | `treatmentName` |
| `applicationClosesAt` | `bookingClosesAt` |
| `availableOnly=true` | 항상 예약 가능한 것만 반환하므로 파라미터 없음 |
| `price` / `originalPrice` | `salePrice` / `regularPrice` |
| `status` (AVAILABLE 등) | 목록에는 없음 (예약 가능한 것만 반환). 빈자리 상세의 `bookable` |
| 데모 `region` ('강남/서초' 등) | `regionCode` 여러 개로 변환 (9장) |
| 데모 `urgency` ('초임박 특가' 등) | `priceTier` |
| 데모 `lat`, `lng` | `latitude`, `longitude` |

## 9. 결정 사항 (2026-10-28)

1. **빈자리의 범위**: 매장이 연 시간대(OPEN) 안의 시각만 목록에 노출한다. 서비스 취지(매장이 등록한 타임세일 빈자리), 알림 이벤트(`NOTIFICATION_EVENT`가 OPEN 행 기준)와 맞고 결과 수가 과도하게 늘지 않는다.
   - 예외 (UNNIE-157): 메인 임박 카드는 8시간 안 빈 시각을 영업시간 기준으로 노출한다. 범위가 짧고 매장당 1건이라 결과 수가 늘지 않는다 (4-1장).
   - 예외 (UNNIE-165): 타임세일(`priceTier=LAST_MINUTE`)도 영업시간 기준으로 찾는다. 메인 임박 카드와 기준이 어긋나지 않게 하고, 실제로 가격이 내려가는 3시간 이내 할인 시술만 보여준다. 메인 임박 카드의 "전체보기"는 3시간 이내 카드가 있을 때만 타임세일로, 없으면 일반 예약하기(`/?view=list&date=all`)로 연결한다.
   - 남은 확인: 빈자리 상세의 시간 선택(`available-times`, B)은 영업시간까지 포함한다. 목록에 없던 시간을 고를 수 있게 둘지는 B 담당과 정한다.
2. **경로·패키지**: API는 `/api/v1/explore/...`, 백엔드 코드는 `domain/explore`. 경로를 화면(메인·index) 이름이 아니라 기능(탐색)과 데이터(빈자리) 이름으로 지어, B의 빈자리 관리 API와 섞이지 않게 한다. 메인·검색·지도 화면이 모두 같은 API를 조건만 바꿔 호출한다. 프런트의 메인 화면 경로는 기존대로 `/`(`ROUTES.HOME`)이다.
3. **지역 필터**: API는 `regionCode`(시군구 5자리)를 여러 개 받고, 화면의 지역 묶음은 프런트 상수(`constants/regions.js`)로 코드 목록에 대응시킨다. 지역 묶음을 바꿔도 서버 수정이 필요 없다.

   | 화면 지역 묶음 | `regionCode` |
   | --- | --- |
   | 강남/서초 | `11680` 강남구, `11650` 서초구 |
   | 홍대/연남 | `11440` 마포구, `11410` 서대문구 |
   | 마곡/강서 | `11500` 강서구 |
   | 성수/건대 | `11200` 성동구, `11215` 광진구 |
   | 잠실/송파 | `11710` 송파구 |
   | 판교/분당 | `41135` 성남시 분당구 |

   매장의 `REGION_CODE`는 매장 등록(A-03) 때 카카오 주소 검색 결과의 법정동 코드 앞 5자리로 채운다. 알림의 지역 구독(`NOTIFICATION_SUBSCRIPTION.REGION_CODE`)도 같은 코드를 쓴다.
4. **지도 범위 조회**: 지도를 움직일 때마다 화면 범위 안만 다시 불러온다 (`minLat`·`maxLat`·`minLng`·`maxLng`, 6장).
5. **이미지 우선순위**: 시술 이미지 → 매장 이미지 → null.
6. **지도 마커**: 매장당 마커 1개, 라벨은 최저 판매가 (C-02).
7. **지도 메인 화면 경로**: 새 경로 `/map`(`ROUTES.MAP`). 공통 파일인 `constants/routes.js`·`AppRouter.jsx`에 경로를 추가하므로 해당 PR은 프런트 파트장 리뷰를 받는다. 홈의 기존 지도 보기(`/?view=map`)는 목록 화면의 보조 보기로 유지한다.
8. **구현 브랜치**: C-01·C-02는 `feature/be/38-vacancy-search-api`, C-03은 `feature/be/39-vacancy-detail-api`.
