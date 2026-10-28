# A-01 메인 노출 기준 및 탐색 API 계약

상태: **C 확정 계약 반영 · 실제 API 및 화면 연결은 담당자 구현 필요** · 갱신일: 2026-10-28 · 관련 이슈: UNNIE-121

메인 목록은 [C 빈자리 탐색·지도 API 계약](C-01-vacancy-search.md)을 사용한다. 기존 `/api/v1/items`와 8개 필드 제안은 아래 계약으로 대체한다. 이 문서 변경은 서버 응답 또는 화면 연결 구현 완료를 의미하지 않는다.

## 1. 담당 범위

| 담당 | 작업 |
| --- | --- |
| A-03 | 주소 선택·변경 시 좌표 변환 후 매장 프로필 저장 요청에 포함 |
| C 백엔드 | 탐색 조회·응답 DTO에 매장명, 가격, 좌표 포함. B의 가용 시간·가격 정책 재사용 |
| C 프런트 | 현재 홈·검색 화면 API 연결, 카드 필드 매핑, 지도 마커 연결 |
| UNNIE-121 | 기존 A-01 문서를 C 계약에 맞추고 매장 좌표 저장부터 목록·지도까지 전달 규칙 명시 |

메인 전용 API를 추가하지 않는다. 서버의 필터·정렬·페이지 결과를 그대로 표시한다. 현재 브랜치에서 제거된 과거 MainPage·useMainStore 구조를 복원하지 않는다.

## 2. 메인 노출 기준

빈자리 한 건은 매장의 시술 1개와 예약 가능한 시작 시각 1개다. 활성 매장·시술의 OPEN 시간대 중 B 정책상 예약 가능한 빈자리만 조회한다. 시간 기준은 서버의 현재 시각과 Asia/Seoul이다.

| 영역 | 요청 조건 | 정렬 | 개수 |
| --- | --- | --- | --- |
| 오늘 | startDate와 endDate를 오늘(KST)로 지정 | START_ASC | 6 |
| 마감 임박 | closingWithinHours=24 | START_ASC | 6 |

- 예약 마감은 `bookingClosesAt`이며 시작 20분 전이다. 마감과 현재 시각이 같으면 제외한다.
- 마감 임박은 `(now, now + 24시간]`에 마감되는 빈자리다. 시작 시각에서 일정 시간을 빼므로 START_ASC로 마감 순서도 맞는다.
- 동률 정렬은 C 계약의 `startsAt ASC, treatmentId ASC`를 따른다.
- 두 영역의 중복 노출을 허용한다. 날짜 변경·화면 재진입 시 다시 조회한다.
- 프런트에서 가격·마감 시각을 계산하여 서버 응답을 대체하지 않는다.

## 3. 목록 API와 응답 필드

`GET /api/v1/explore/vacancies`를 비로그인으로 조회하며 `ApiResponse<VacancyPageResponse>`를 받는다. 전체 필드·필터는 C 계약 3장을 따른다.

```http
GET /api/v1/explore/vacancies?startDate=2026-10-28&endDate=2026-10-28&sort=START_ASC&page=0&size=6
GET /api/v1/explore/vacancies?closingWithinHours=24&sort=START_ASC&page=0&size=6
```

`availableOnly`와 `CLOSING_ASC`는 보내지 않는다. `closingWithinHours`는 1~72, `size`는 1~50이다. 날짜를 생략하면 오늘부터 7일이 기본이므로 오늘 영역은 두 날짜를 모두 명시한다. 날짜는 호출 시 KST로 계산한다.

응답의 `data.content` 항목 예시(전체 필드는 C 계약 참조):

```json
{
  "vacancyId": "3-202610281800",
  "shopId": 1,
  "shopName": "언니네 네일",
  "address": "서울특별시 강남구 테헤란로 1",
  "latitude": 37.4979,
  "longitude": 127.0276,
  "treatmentId": 3,
  "treatmentName": "젤 네일 원컬러 + 케어",
  "startsAt": "2026-10-28T18:00:00+09:00",
  "bookingClosesAt": "2026-10-28T17:40:00+09:00",
  "regularPrice": 50000,
  "salePrice": 32500,
  "imageUrl": null
}
```

| 기존 A 제안 | C 확정 필드 | 사용 규칙 |
| --- | --- | --- |
| id | vacancyId | 문자열. 시술 ID와 시작 시각 조합이며 카드 key에 사용 |
| title | treatmentName | 카드 제목 |
| applicationClosesAt | bookingClosesAt | 오프셋 포함 ISO-8601 (+09:00) |
| shopName | shopName | SHOP.NAME |
| price | salePrice | 서버 PriceCalculator.quote의 현재 적용 판매가 |
| originalPrice | regularPrice | 정상가 |
| latitude, longitude | latitude, longitude | SHOP의 좌표를 JSON number로 반환 |

- 같은 시술의 여러 시작 시각을 구분해야 하므로 treatmentId만으로 목록 key를 만들지 않는다.
- 가격은 서버가 반환한 숫자를 그대로 표시한다. 문자열 숫자 변환이나 임의의 기본 가격으로 오류를 숨기지 않는다.
- SHOP의 좌표는 DDL과 매장 저장 요청에서 필수다. 기존 A 제안의 좌표 null 허용은 채택하지 않는다. 비정상 응답의 누락·비유한 값·범위 밖 좌표는 마커로 만들지 않는다.
- 페이지 응답은 content, page, size, totalElements, totalPages, hasNext다. 빈 결과는 content: []로 처리한다.
- 잘못된 파라미터는 C 계약의 400 / COMMON_400을 사용한다. 과거 제안 SEARCH_001을 새로 추가하지 않는다.

## 4. A-03 주소·좌표 저장 흐름

현재 `ShopUpdateRequest`와 `Shop.updateProfile`은 latitude·longitude를 받아 저장한다. SHOP에 LATITUDE·LONGITUDE 컬럼이 이미 있으므로 이 요구를 위한 스키마 변경은 필요 없다. 상세 요청·응답은 [매장 API](shops.md)를 따른다.

주소만 전송하면 현재 백엔드가 좌표를 변환해 주는 구조는 아니다. 현재 요청 계약을 유지할 때 프런트 연결은 다음과 같이 구현한다.

1. 주소를 선택·변경하면 해당 주소를 한 번 좌표로 변환한다. 매장 프로필 저장 버튼을 누를 때마다 변환하지 않는다.
2. 주소와 변환 결과를 함께 폼에 보관한다. 주소가 다시 바뀌면 이전 좌표를 무효화하고 새 주소의 좌표를 구한다. 늦게 도착한 이전 주소의 변환 결과는 버린다.
3. 변환 실패 또는 좌표 누락 시 저장을 막고 재시도 안내를 표시한다. 이전 주소의 좌표와 새 주소를 함께 저장하지 않는다.
4. PUT /api/v1/shops/{shopId}에 address, addressDetail, regionCode, latitude, longitude를 포함한다. regionCode는 C 계약대로 주소 검색 결과의 법정동 코드 앞 5자리다.
5. 주소가 유지되는 다른 프로필 수정에서는 저장된 좌표를 재사용한다. latitude는 -90~90, longitude는 -180~180이며 소수점 7자리까지 허용한다.

고객 목록 조회와 지도 렌더링에서는 주소를 다시 좌표로 변환하지 않는다.

## 5. 카드·지도 연결 규칙

- 업무 API 호출은 컴포넌트 → Zustand store 액션 → api 모듈 → axiosInstance를 거친다.
- 현재 VacancyMap은 데모 service.lat/lng, service.shop, service.price를 사용한다. 실제 C 응답 연결 시 latitude/longitude, shopName, salePrice를 화면에 매핑해야 한다.
- KakaoMap의 마커 입력은 `{ id, lat, lng, label, ariaLabel }`이다. 좌표는 `lat: item.latitude`, `lng: item.longitude`로 전달한다. 위도 0·경도 0도 유효하므로 truthy 검사로 제외하지 않는다.
- 홈 목록의 보조 지도는 vacancyId로 빈자리를 구분한다. 별도 /map 화면의 매장당 마커 1개·최저가 표시는 C-02 GET /api/v1/explore/shops를 사용한다. 두 화면의 마커 집계 기준을 혼용하지 않는다.
- 목록이 비면 빈 결과 안내를 표시하며 선택 카드의 속성을 읽지 않는다. 실패를 빈 결과로 표시하지 않고 재시도 동작을 제공한다.
- 지도 키 또는 SDK 로딩 실패 시 C 계약대로 실제 목록을 표시한다. 예시 좌표나 고정 위치 핀으로 실제 매장 위치를 대신하지 않는다.
- 카드 선택 시 treatmentId와 startsAt으로 C-03 상세를 조회한다. 목록 노출만으로 현재 예약 가능 여부를 보장하지 않는다.

## 6. 후속 구현 수용 기준

다음은 구현 후 확인할 항목이며 이 문서 변경에서 실행·완료한 검증이 아니다.

- [ ] 매장 주소 변경 후 새 좌표가 저장되고 프로필만 수정하면 기존 좌표가 유지된다.
- [ ] 주소 변환 실패·이전 요청 응답 경합 시 잘못된 좌표가 저장되지 않는다.
- [ ] 탐색 DTO·조회 결과에 SHOP의 매장명·좌표와 정책 적용 판매가가 포함된다.
- [ ] 같은 시술의 여러 시작 시각이 서로 다른 vacancyId로 표시된다.
- [ ] 실제 목록 좌표가 KakaoMap의 lat/lng에 올바른 순서로 전달된다.
- [ ] 좌표 0은 허용하고 누락·범위 밖 좌표는 마커에서 제외한다.
- [ ] 오늘 KST 경계·예약 마감 경계·정렬·페이지 처리를 서버에서 검증한다.
- [ ] 로딩·빈 목록·오류·재시도와 지도 SDK 실패 시 목록 대체 표시를 확인한다.

과거 문서에 기록된 MainPage·useMainStore·frontend/MAIN-INTEGRATION.md 및 관련 테스트 파일은 현재 브랜치에서 제거되어 있다. 과거 실행 기록을 현재 구현의 검증 결과로 사용하지 않는다.
