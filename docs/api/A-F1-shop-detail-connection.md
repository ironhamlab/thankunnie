# 매장 상세 화면 연결 (UNNIE-49)

시술 상세 화면을 매장 상세 화면으로 바꿨다. 매장 대표 이미지·이름·주소 아래에 메뉴 버튼(예약하기, 상세정보)을 좌우로 두고,
고른 메뉴의 내용만 아래에 보여준다. 처음 들어오면 예약하기를 연다. 예약은 시술 선택(1단계) → 날짜·30분 단위 시작 시간 선택(2단계) 순서로 한 단계씩 보여준다.

## 화면 흐름

```text
/shops/:shopId                     이미지·매장명·주소, 메뉴 [예약하기(넓게)] [상세정보(좁게)]
  ├ 예약하기 (기본)
  │   → 1단계 시술 선택              판매 중인 시술, 정상 가격
  │   → 2단계 날짜·시간              선택한 시술 + [시술 변경], 달력(오늘 포함 60일), 30분 단위 시작 시각
  │                                  오른쪽 예약 요약: 고른 시각의 서버 가격·예약금·잔금·예약 가능 여부
  └ 상세정보                         주소·연락처·영업시간·소개·공지
```

- 주소는 `/shops/{shopId}`를 쓴다. 메뉴·시술·단계·날짜·시간 선택은 화면 상태로만 두고 주소에 붙이지 않는다.
  - 처음 들어오면 예약하기 1단계이며, 날짜는 오늘부터 7일 중 첫 영업일이 미리 잡히고 시간은 비어 있다.
  - 탐색 화면(C)에서 들어올 때는 `?date=&treatmentId=&startsAt=`로 처음 선택값을 받을 수 있다. 시술·시작 시각이 있으면 2단계가 그 날짜·시간이 골라진 채로 열리고, `date`만 있으면 1단계에 그 날짜가 잡힌다. 형식·범위가 맞지 않거나 판매 중이 아닌 시술이면 기본 처음 상태로 연다. 이후 선택은 주소에 다시 쓰지 않는다.
  - 새로고침하거나 다른 매장으로 이동하면 선택이 처음 상태로 돌아간다. 주소에 처음 선택값이 있으면 그 값으로 돌아간다.
- 시술을 고르면 2단계로 넘어간다. 단계 표시("1. 시술 선택", "2. 날짜·시간 선택")를 눌러 바로 오갈 수 있고, 2단계는 시술을 고른 뒤에만 누를 수 있다. "시술 변경"도 1단계로 돌아간다.
- 고른 시작 시각부터 소요 시간 동안의 칸을 함께 표시한다. 예: 60분 시술을 12:00에 고르면 12:00·12:30이 함께 잡힌 것으로 보인다. 실제 예약 막힘은 서버(B `AvailabilityCalculator`)가 예약 구간 `[시작, 시작 + 소요 시간)` 전체로 판단한다.
- 예약 요약은 2단계에서만 보인다. 상세정보·1단계일 때는 한 열로 가운데 정렬한다.
- 시술이나 날짜를 바꾸면 고른 시간은 지운다.
- 예약 요약의 "예약하기" 버튼은 예약 대기열에 선 뒤 예약 생성(점유)을 부르고 결제 화면으로 이동한다. 다른 고객이 결제 중인 시각이면 "대기하기"로 바뀐다 (`reservations.md`).
- 지도 보기 버튼은 두지 않는다. 매장 위치 지도는 상세정보 탭에 있다 (`C-01-vacancy-search.md` 7장).

## 사용하는 API

| 단계 | API | 인증 |
| --- | --- | --- |
| 매장 정보·시술 목록 | `GET /api/v1/explore/shops/{shopId}` (이번에 추가) | 불필요 |
| 시작 가능 시각 | `GET /api/v1/available-times?shopId=&date=&durationMinutes=` (B) | 불필요 |
| 고른 시각의 가격 | `GET /api/v1/explore/vacancies/{treatmentId}?startsAt=` (C-03) | 불필요 |

호출 경로: 화면 → `useShopDetailStore`·`useVacancyDetailStore` → `shopDetailApi`·`vacancyDetailApi` → 공통 `axiosInstance`.

### 시작 가능 시각 `GET /api/v1/available-times` (B)

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `shopId`, `date`, `durationMinutes` | number, string(YYYY-MM-DD), number | 요청 값 그대로 |
| `startTimes` | string[] (ISO-8601, +09:00) | 30분 단위 시작 가능 시각, 오름차순. 영업시간·매장이 연 시간 안에서 시술이 끝나고, 닫은 시간·다른 예약과 겹치지 않으며, 시작 20분 전이 지나지 않은 시각 |
| `extendedStartTimes` | string[] (ISO-8601, +09:00) | `startTimes` 중 영업시간만으로는 안 되고 매장이 연 시간(OPEN) 덕분에 가능한 시각, 오름차순. 시술이 요일 영업시간 밖으로 조금이라도 넘어가면 포함하고(예: 20시 마감, 120분 시술 18:30 시작), 휴무일에 연 시간은 모두 포함한다. 영업시간 안에서 연 시간은 포함하지 않는다. 없으면 빈 배열 |
| `heldStartTimes` | string[] (ISO-8601, +09:00) | 다른 고객이 결제 중(유효한 HOLD 점유)이라서만 지금 시작할 수 없고, 그 점유와 시작·끝 시각이 정확히 같은 시각, 오름차순. 점유와 일부만 겹치는 시각은 넣지 않는다(예약 불가). 점유가 결제 없이 풀리면 예약할 수 있어 예약 대기열(`reservations.md`)에 설 수 있다. 확정 예약·닫힌 시간·영업 외 시간과 겹치면 넣지 않는다. `startTimes`와 겹치지 않으며 없으면 빈 배열 |

- 매장이 적은 열기 사유는 응답에 넣지 않는다. 연장 표시는 정해진 문구로 한다.

### 매장 상세 `GET /api/v1/explore/shops/{shopId}`

- 노출 중인 매장(`SHOP.STATUS = ACTIVE`)의 안내, 월~일 영업시간 7개, 판매 중인 시술(`TREATMENT.STATUS = ACTIVE`, 시술 ID 순)을 돌려준다.
- 저장하지 않은 요일은 휴무(`closed: true`, 시각 null)로 채운다. 매장 영업시간 조회와 같은 형식이다.
- 시술의 `price`는 일반 판매가다. 초임박 구간 가격은 시각을 고른 뒤 C-03으로 확인한다.
- 매장이 없거나 비활성이면 404 `COMMON_404`("매장을 찾을 수 없습니다.").
- 코드: `domain/explore` (`ExploreShopController`, `ExploreShopDetailService`, `ExploreShopDetailRepository`). 매장·시술·영업시간 엔티티는 읽기만 한다.

```json
{
  "shopId": 1, "shopName": "언니네 네일", "categoryId": 1, "categoryName": "네일",
  "regionCode": "11680", "address": "서울특별시 강남구 테헤란로 1", "addressDetail": "2층",
  "latitude": 37.4979, "longitude": 127.0276, "phone": "02-111-1111",
  "description": "젤네일·케어 전문", "notice": "예약 시간 10분 전 도착해 주세요", "imageUrl": null,
  "businessHours": [{ "dayOfWeek": "MON", "closed": false, "openTime": "10:00", "closeTime": "20:00" }],
  "treatments": [{
    "treatmentId": 1, "name": "젤네일 원컬러", "description": "기본 원컬러 젤네일",
    "treatmentType": "NORMAL", "durationMinutes": 60, "regularPrice": 35000, "price": 30000,
    "modelCondition": null, "photoNotice": null, "imageUrl": null
  }]
}
```

- `modelCondition`(참여 조건), `photoNotice`(촬영 안내)는 `treatmentType = MODEL`일 때만 값이 있고 NORMAL이면 null이다. `photoNotice`는 초보 시술 모집 상세(UNNIE-52)를 위해 추가했다.

## 초보 시술 모집 상세 (UNNIE-52)

```text
/shops/:shopId/models                매장 상세와 같은 머리(이미지·매장명·메뉴)
  ├ 예약하기 (기본)
  │   → 1단계 시술 선택              그 매장의 판매 중인 MODEL 시술만
  │   → 2단계 모집 안내 확인          설명·참여 조건·촬영 안내, [필수] 확인 체크 → [다음]
  │   → 3단계 날짜·시간              선택한 시술 + [시술 변경], 달력·시작 시각, 오른쪽 예약 요약
  └ 상세정보                         매장 상세와 같음
```

- 매장 상세(`/shops/:shopId`)의 예약하기는 일반 시술 다음에 초보 시술까지 모든 시술을 보여주고, 초보 시술을 고르면 모집 안내 확인 단계를 거친다. 초보 시술 모집 상세는 MODEL 시술만 보여준다 (UNNIE-247). 같은 `ShopDetailPage`를 `model` 값으로 나눠 쓰며 API도 같다.
- 확인 체크를 해야 3단계로 갈 수 있다. 시술을 바꾸면 체크가 풀린다. 체크는 화면 상태로만 두며 서버에 저장하지 않는다.
- 처음 선택값은 매장 상세와 같이 `?date=&treatmentId=&startsAt=`로 받는다. 시술이 골라진 채로 들어와도 2단계(안내 확인)부터 연다.
- `utils/exploreVacancy.js`의 `shopPagePath(shopId, { ..., model: true })`로 이 주소를 만든다. 탐색 목록의 MODEL 빈자리 시간 버튼과, 초보 시술 모집 목록(`?type=MODEL`)의 매장 카드·지도 "매장 보기"가 이 화면으로 간다.

## 시간 슬롯

- 그 날 영업시간 안에서 시술이 끝날 수 있는 30분 단위 시작 시각을 모두 표시한다 (`openTime` ~ `closeTime - 소요 시간`).
- 예약 가능 여부는 `available-times` 응답만 따른다. 응답에 없는 시각은 비활성(취소선)으로 보인다. 지난 시각, 시작 20분 전 마감, 닫은 시간, 다른 예약과 겹치는 시간이 여기에 해당한다.
- 매장이 영업시간 밖으로 연 시간(OPEN)은 `available-times` 응답에 들어 있으므로 슬롯에 함께 표시한다.
- 그중 영업시간 밖 연장 영업 덕분에 가능한 시각은 응답의 `extendedStartTimes`로 따로 온다(시술이 영업시간 밖으로 넘어가면 포함, 휴무일에 연 시간은 모두 포함). 이 시각 칸에는 "연장" 표시를 붙이고, 시간 목록 위에 "평소 영업시간(10:00~20:00) 밖에도 연 시간이 있어요. '연장' 표시 시간을 확인해 주세요."(휴무일이면 "휴무일이지만 매장이 따로 연 시간이에요") 한 줄을 보여준다. 연 시간 끝에 다른 예약이 있으면 가능 시각만으로는 실제 연장 마감을 알 수 없어 마감 시각은 적지 않는다. 매장이 적은 사유 문구는 고객에게 보여주지 않는다.
- 날짜는 월 단위 달력으로 고른다. 서버 예약 가능 범위와 같게 오늘 포함 60일까지 고를 수 있고, 그 밖의 날짜와 달은 비활성이다.
- 휴무 요일은 달력에 "휴무"로 표시한다. 매장이 휴무일에 따로 연 시간이 있을 수 있어 고를 수는 있고, 슬롯이 없으면 안내를 보여준다.
- 빈자리 목록(C-01)도 UNNIE-220부터 영업시간 기준이라, 이 화면과 같은 시각이 나온다 (C-01 문서 9장 1번).

## 매장 상세로 연결하기

매장 상세로 보낼 때는 매장 ID를 넘기고, 고른 날짜·시술·시작 시각이 있으면 쿼리로 함께 넘긴다.
탐색 화면은 `utils/exploreVacancy.js`의 `shopPagePath()`로 이 주소를 만든다.

```jsx
const to = generatePath(ROUTES.SHOP_DETAIL, { shopId: vacancy.shopId });
// 시간까지 고른 경우: /shops/1?date=2026-10-29&treatmentId=1&startsAt=2026-10-29T19:00:00+09:00
const toSlot = shopPagePath(vacancy.shopId, {
  date: '2026-10-29',
  treatmentId: vacancy.treatmentId,
  startsAt: vacancy.startsAt,
});
```

## 공통 파일 변경 (프런트 파트장 리뷰 필요)

- `constants/routes.js`: `SHOP_DETAIL: '/shops/:shopId'` 추가
- `routes/AppRouter.jsx`: 이전 시술 상세 라우트(`/services/:id`)를 매장 상세 라우트(`/shops/:shopId`)로 교체
  - 남겨 두었던 `ROUTES.SERVICE_DETAIL` 상수는 참조하는 화면이 없어져 UNNIE-207에서 지웠다.

## 가격 표시

- 시술 목록에는 정상 가격(`regularPrice`)만 표시한다.
- 시간을 고르면 예약 요약에 C-03 응답 값(정상 가격, 할인 금액·할인율, 최종 금액, 예약금, 잔금)을 그대로 표시한다. 할인은 `salePrice < regularPrice`일 때만 보이고, 가격 구간이 `IMMINENT`·`LAST_MINUTE`이면 "임박"·"초임박 특가" 라벨을 붙인다.
- 현재 가격 규칙(B-05 `SalePrices`)에서는 시작 8시간 초과 전(`NORMAL`)에도 일반 판매가(`treatment.price`)가 적용되고, 시작 3시간 전부터 초임박 판매가가 적용된다. 따라서 일반 시간에도 매장이 정한 할인이 요약에 보일 수 있다.

## 찜

- 매장명 오른쪽의 "찜" 버튼은 매장 알림 구독(`NOTIFICATION_SUBSCRIPTION`, `subscriptionType = STORE`)을 등록·해제한다. 마이페이지 알림 조건과 같은 데이터다.
  - 등록: `POST /api/v1/notifications/subscriptions` (`shopId`, 희망 시간은 오늘 00:00 ~ 60일 뒤 00:00)
    - 희망 시간이 있는 구독(알림 받기 "예")은 알림 문자 수신 동의가 필요하다. 프론트는 알림 조건 입력 전에 동의 모달을 띄우고, 서버도 동의가 없으면 `TERMS_003`(409)을 돌려준다 (`terms.md`). 희망 시간 없는 찜("아니요")은 동의 없이 등록된다.
  - 해제: `DELETE /api/v1/notifications/subscriptions/{subscriptionId}`
  - 상태: `GET /api/v1/notifications/subscriptions`에서 같은 매장의 STORE 구독이 있으면 찜한 상태로 본다.
- 로그인하지 않았으면 로그인 화면으로 보내고, 로그인 뒤 이 화면으로 돌아온다. 개발 서버 테스트 계정도 로그인으로 본다.
- 마이페이지 알림의 "알림 전 찜 매장" 탭(`FavoriteShopList`)이 같은 구독 데이터를 보여준다. 요청·응답 형식과 오류는 `notification-subscriptions.md`.
