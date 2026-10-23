# A-03 매장 관리 (#27)

- `GET /api/v1/shops/{shopId}`: 본인 매장 정보 조회, 200
- `PUT /api/v1/shops/{shopId}`: 본인 매장 정보 전체 저장·수정, 200
- 인증 사용자의 `requireShopOwner`로 조회·수정 모두 본인 매장만 허용한다. 고객·관리자·다른 매장 사장님은 403, 비로그인은 401이다.
- 매장이 없으면 404, 필수값 누락·길이 초과·잘못된 좌표·존재하지 않는 카테고리는 400이다.
- 응답은 `ApiResponse<ShopDetailResponse>`이며 로그인 정보는 노출하지 않는다.

## 요청 예시

```json
{
  "categoryId": 1,
  "name": "언니 미용실",
  "regionCode": "11680",
  "address": "서울특별시 강남구 테헤란로 1",
  "addressDetail": "2층",
  "latitude": 37.4979000,
  "longitude": 127.0276000,
  "phone": "02-000-0000",
  "description": "매장 소개",
  "notice": "예약 후 방문해 주세요",
  "imageUrl": null
}
```

응답 data는 요청 필드에 `shopId`가 추가된다. 위도는 -90~90, 경도는 -180~180이며 소수점 7자리까지 허용한다. 필수 필드는 categoryId, name, regionCode, address, latitude, longitude, phone이다. 선택 필드는 생략하거나 null로 보내면 지워진다.

## 계정 및 스키마 전제

현재 SHOP 테이블은 매장과 사장님 로그인 계정이 한 행이며 필수 프로필을 함께 가진다. 이 API는 이미 생성된 사장님 계정에 연결된 매장 프로필을 저장한다. 새 로그인 계정 생성·비밀번호 설정은 인증/가입 기능의 범위이며 이 API에서 처리하지 않는다. OWNER 인증 주체의 shopId와 실제 SHOP 행이 먼저 준비되어 있어야 한다.

기존 스키마를 그대로 사용하므로 DB 변경 SQL은 없다. CATEGORY도 사전에 준비되어 있어야 한다. local/test에서는 `X-Test-User: OWNER_1`(shopId=1), `OWNER_2`(shopId=2)로 권한 차단을 확인할 수 있다. 테스트 사용자 헤더는 DB 행을 자동 생성하지 않는다.
