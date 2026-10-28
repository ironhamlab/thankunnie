# 인증 API (UNNIE-113)

> 기준: `backend/src/main/java/com/ucamp/app/domain/auth/controller/AuthController.java`
> 응답은 공통 `ApiResponse` 형식이다. 오류 코드는 `error-codes.md`의 COMMON·AUTH 참고.

## 계정 구조

| 역할 | 계정 위치 | 로그인 API | 토큰의 userId | 토큰의 shopId |
|---|---|---|---|---|
| 고객 (CUSTOMER) | `APP_USER` (ROLE=USER) | `POST /api/v1/auth/login` | USER_ID | null |
| 관리자 (ADMIN) | `APP_USER` (ROLE=ADMIN) | `POST /api/v1/auth/login` | USER_ID | null |
| 사장님 (OWNER) | `SHOP` (LOGIN_ID·PASSWORD_HASH) | `POST /api/v1/auth/shops/login` | SHOP_ID | SHOP_ID |

- 회원과 매장은 테이블이 달라 아이디 중복도 각 테이블 안에서만 검사한다. 같은 문자열이 고객 아이디와 매장 아이디로 동시에 존재할 수 있다.
- 사장님은 APP_USER 행이 없어 `userId` 자리에 매장 ID를 넣는다. 고객 전용 API는 `requireRole(CUSTOMER)`로 막혀 있어 고객 ID와 숫자가 겹쳐도 다른 고객 데이터에 접근하지 않는다.
- 가입은 관리자 계정을 만들지 않는다. 관리자는 DB에 직접 넣는다.

## 토큰

- HS256 JWT, 유효기간 기본 24시간(`app.jwt.access-token-validity`). 리프레시 토큰은 없다.
- 요청 헤더: `Authorization: Bearer {accessToken}`
- 만료·변조·형식 오류 토큰은 비로그인으로 처리한다. 로그인이 필요한 API에서 `COMMON_401`.
- 로그아웃은 클라이언트가 토큰을 지운다. 서버에 로그아웃 API는 없다.
- 서명 키: `JWT_SECRET` 환경변수(32바이트 이상). prod는 미설정 시 기동하지 않는다. local·test는 `application.yml`의 더미 키를 쓴다.
- local·test 프로필에서는 기존 테스트 사용자(`X-Test-User` 또는 `Authorization: Bearer OWNER_1` 등)도 그대로 동작한다. 테스트 사용자 값이 아니면 JWT로 검증한다.

## 공통 응답 data (가입·로그인 성공)

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresAt": "2026-10-29T10:00:00+09:00",
  "userId": 1000,
  "role": "CUSTOMER",
  "shopId": null
}
```

## POST /api/v1/auth/signup — 고객 회원가입

가입 후 바로 로그인된 토큰을 반환한다. `201 Created`.

```json
{
  "loginId": "unnie01",
  "password": "password1!",
  "name": "김언니",
  "phone": "01012345678"
}
```

| 필드 | 규칙 |
|---|---|
| loginId | 영문 소문자·숫자 4~20자 |
| password | 8~64자 |
| name | 필수, 50자 이하 |
| phone | 휴대폰 번호(010·011·016~019), 하이픈 선택. `010-1234-5678` 형식으로 저장. 문자 알림 수신 번호 |

오류: 형식 위반 `COMMON_400`, 아이디 중복 `AUTH_001`(409)

## POST /api/v1/auth/login — 고객·관리자 로그인

```json
{ "loginId": "customer1", "password": "test1234!" }
```

오류: 아이디 없음·비밀번호 불일치 `AUTH_002`(401), 비활성 회원 `AUTH_003`(403)

## POST /api/v1/auth/shops/signup — 사장님(매장) 회원가입

`SHOP`의 필수 컬럼을 한 번에 넣어야 해서 계정과 매장 정보를 함께 받는다. 매장 정보 검증은 매장 수정 API(`shops.md`)와 같다. 대표 이미지는 가입 후 이미지 API로 올린다. `201 Created`.

```json
{
  "loginId": "nailshop",
  "password": "password1!",
  "categoryId": 1,
  "name": "언니네 네일",
  "regionCode": "11680",
  "address": "서울특별시 강남구 테헤란로 1",
  "addressDetail": "2층",
  "latitude": 37.4979000,
  "longitude": 127.0276000,
  "phone": "02-111-1111",
  "description": "젤네일·케어 전문",
  "notice": "예약 시간 10분 전 도착해 주세요"
}
```

| 필드 | 규칙 |
|---|---|
| loginId, password | 고객 가입과 같음 |
| categoryId | 필수, 존재하는 카테고리 (`GET /api/v1/auth/shop-categories`) |
| name | 필수, 100자 이하 |
| regionCode | 필수, 20자 이하. 구독 알림의 지역 매칭 기준 |
| address | 필수, 300자 이하 |
| addressDetail | 선택, 200자 이하 |
| latitude / longitude | 필수, -90~90 / -180~180, 소수점 7자리 이하 |
| phone | 필수, 20자 이하 (유선 번호 가능) |
| description, notice | 선택, 2000자 이하 |

오류: 형식 위반·없는 카테고리 `COMMON_400`, 아이디 중복 `AUTH_001`(409)

## POST /api/v1/auth/shops/login — 사장님(매장) 로그인

요청 형식은 고객 로그인과 같다. 응답의 `role`은 `OWNER`, `userId`·`shopId`는 매장 ID.

`SHOP.STATUS`는 매장 노출·판매 여부라서 로그인 판단에 쓰지 않는다. INACTIVE 매장도 로그인해 매장을 관리할 수 있다.

오류: 아이디 없음·비밀번호 불일치 `AUTH_002`(401)

## GET /api/v1/auth/shop-categories — 매장 가입용 카테고리

로그인 없이 조회한다.

```json
[ { "categoryId": 1, "name": "네일" }, { "categoryId": 5, "name": "바버" } ]
```

## GET /api/v1/auth/me — 현재 사용자

기존과 같다. 비로그인이면 `COMMON_401`.

## 테스트 계정 (local 초기 데이터)

비밀번호는 모두 `test1234!`. 로컬·시연 전용이다.

| 아이디 | 로그인 API | 역할 |
|---|---|---|
| customer1, customer2 | `/auth/login` | CUSTOMER |
| admin | `/auth/login` | ADMIN |
| owner1 (매장 1), owner2 (매장 2) | `/auth/shops/login` | OWNER |
