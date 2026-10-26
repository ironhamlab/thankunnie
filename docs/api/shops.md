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

응답 data는 요청 필드에 `shopId`가 추가된다. 위도는 -90~90, 경도는 -180~180이며 소수점 7자리까지 허용한다. 필수 필드는 categoryId, name, regionCode, address, latitude, longitude, phone이다. 선택 필드는 생략하거나 null로 보내면 지워진다. 단, 대표 이미지는 별도 API로 관리한다. 프로필 수정 시 imageUrl은 생략하거나 null만 허용하며 기존 이미지를 유지한다. URL을 직접 지정하면 400이다.

## A-07 대표 이미지 (UNNIE-46)

| 메서드·경로 | 용도 | 성공 응답 |
| --- | --- | --- |
| `PUT /api/v1/shops/{shopId}/image` | 본인 매장 대표 이미지 업로드·교체 | 200, `ApiResponse<ShopImageResponse>` |
| `DELETE /api/v1/shops/{shopId}/image` | 본인 매장 대표 이미지 삭제 | 204, 본문 없음 |
| `GET /api/v1/shops/{shopId}/images/{fileName}` | 현재 대표 이미지 공개 조회 | 200, `image/jpeg` 또는 `image/png` 바이너리 |

- PUT은 `multipart/form-data`의 `file` 필드로 파일 1개를 보낸다. JSON URL 요청은 받지 않는다.
- JPEG(`.jpg`, `.jpeg`, `image/jpeg`)·PNG(`.png`, `image/png`)만 허용한다. 확장자 대소문자는 구분하지 않는다.
- 최대 파일 크기는 **5MiB(5 × 1024 × 1024바이트), 경계값 포함**, 최대 해상도는 **1,600만 화소**다. 전체 multipart 요청 한도는 6MiB다.
- 누락·빈 파일·크기 초과·형식 불일치·실제 이미지 디코딩 실패는 `400 / COMMON_400`이다. 파일 이름과 MIME 타입뿐 아니라 실제 이미지 형식과 내용을 확인한다.
- PUT·DELETE는 비로그인 401, 고객·관리자·다른 매장 사장님 403, 존재하지 않는 본인 매장 404다. 이미지가 없는 매장에 DELETE를 반복해도 204다.
- 업로드 성공 예시: `data: {"shopId": 1, "imageUrl": "/api/v1/shops/1/images/{서버에서 생성한 파일명}.png"}`. 프론트는 API 서버 기준 상대 경로를 사용한다. GET 매장 정보의 imageUrl도 같은 경로를 반환한다.
- 조회 API는 현재 DB에 연결된 이미지만 제공한다. 교체·삭제된 URL이나 잘못된 파일명은 404다. 응답에 `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`를 설정한다.

### 실패 시 유지 및 동시 수정

1. 권한과 매장 존재를 확인한 뒤, 파일을 검증하고 새 이름으로 저장한다.
2. 파일 저장 성공 후에만 SHOP.IMAGE_URL을 변경한다. 검증·저장 실패 시 기존 URL과 파일을 유지한다.
3. DB 커밋 후 기존 파일을 삭제한다. DB 롤백 시 기존 URL·파일을 유지하고 새 파일을 정리한다.
4. 이미지 교체·삭제 및 매장 프로필 수정은 같은 SHOP 행의 `PESSIMISTIC_WRITE` 잠금을 사용한다. 동시 요청이 기존 URL을 덮어쓰거나 서로의 파일을 지우지 않도록 직렬화한다.

기존 외부 URL은 교체·삭제 시 연결만 해제하며 외부 파일에는 접근하지 않는다. 파일 정리 실패는 서버 경고 로그로 남기며 이미 커밋된 결과는 유지한다. 프로세스 강제 종료나 파일 정리 실패로 미참조 파일이 남을 수 있어 운영 시 별도 점검이 필요하다.

### 저장 경로·운영 전제

- 별도 라이브러리나 DB 스키마 변경 없이 기존 `SHOP.IMAGE_URL`과 JDK ImageIO를 사용한다.
- 저장 경로 설정은 `app.shop-image.directory`이며 기본값은 `${user.home}/.thankunnie/shop-images`다. 로컬 예시는 `application-local.yml.example`을 참고한다.
- 서버가 생성한 파일명만 파일 접근에 사용하며 원본 파일명은 경로로 사용하지 않는다.
- 현재 구현은 서버 파일시스템 저장이다. 운영 시 이 디렉터리를 영속 볼륨에 연결하고 백업해야 한다. 여러 서버를 사용하면 모든 서버가 같은 디렉터리를 읽을 수 있는 공유 볼륨이 필요하다.
- 실제 인증 정책을 적용할 때는 현재 대표 이미지의 GET 경로를 공개 조회 대상으로 유지한다. PUT·DELETE에는 기존 B-01 인증 주체와 매장 권한 검사를 적용한다.
- 이번 범위는 백엔드 API이며 프론트 업로드 화면 연결은 포함하지 않는다.

## 계정 및 스키마 전제

현재 SHOP 테이블은 매장과 사장님 로그인 계정이 한 행이며 필수 프로필을 함께 가진다. 이 API는 이미 생성된 사장님 계정에 연결된 매장 프로필을 저장한다. 새 로그인 계정 생성·비밀번호 설정은 인증/가입 기능의 범위이며 이 API에서 처리하지 않는다. OWNER 인증 주체의 shopId와 실제 SHOP 행이 먼저 준비되어 있어야 한다.

기존 스키마를 그대로 사용하므로 DB 변경 SQL은 없다. CATEGORY도 사전에 준비되어 있어야 한다. local/test에서는 `X-Test-User: OWNER_1`(shopId=1), `OWNER_2`(shopId=2)로 권한 차단을 확인할 수 있다. 테스트 사용자 헤더는 DB 행을 자동 생성하지 않는다.
