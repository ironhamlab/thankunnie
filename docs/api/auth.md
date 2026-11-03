# 인증 API (UNNIE-113)

> 기준: `backend/src/main/java/com/ucamp/app/domain/auth/controller/AuthController.java`
> 응답은 공통 `ApiResponse` 형식이다. 오류 코드는 `error-codes.md`의 COMMON·AUTH 참고.

## 계정 구조

| 역할 | 계정 위치 | 로그인 API | 토큰의 userId | 토큰의 shopId |
|---|---|---|---|---|
| 고객 (CUSTOMER) | `APP_USER` (ROLE=USER) | `POST /api/v1/auth/login` | USER_ID | null |
| 관리자 (ADMIN) | `APP_USER` (ROLE=ADMIN) | `POST /api/v1/auth/login` | USER_ID | null |
| 매장 (OWNER) | `SHOP` (LOGIN_ID·PASSWORD_HASH) | `POST /api/v1/auth/shops/login` | SHOP_ID | SHOP_ID |

- 회원과 매장은 테이블이 달라 아이디 중복도 각 테이블 안에서만 검사한다. 같은 문자열이 고객 아이디와 매장 아이디로 동시에 존재할 수 있다.
- 매장은 APP_USER 행이 없어 `userId` 자리에 매장 ID를 넣는다. 고객 전용 API는 `requireRole(CUSTOMER)`로 막혀 있어 고객 ID와 숫자가 겹쳐도 다른 고객 데이터에 접근하지 않는다.
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
  "phone": "01012345678",
  "agreedTerms": ["AGE_14", "SERVICE", "PRIVACY", "THIRD_PARTY", "SMS_NOTIFICATION"],
  "phoneVerificationId": "휴대폰 인증 API에서 받은 verificationId"
}
```

| 필드 | 규칙 |
|---|---|
| loginId | 영문 소문자·숫자 4~20자 |
| password | 8~64자, UTF-8 72바이트 이하 (BCrypt 한도. 한글은 24자까지) |
| name | 필수, 50자 이하 |
| phone | 휴대폰 번호(010·011·016~019), 하이픈 선택. `010-1234-5678` 형식으로 저장. 문자 알림 수신 번호 |
| phoneVerificationId | 필수. 해당 phone으로 인증 완료한 verificationId. 인증 완료 후 10분 이내, 가입 성공 시 1회 소비 |
| agreedTerms | 필수. 동의한 약관 코드 배열. 필수 약관(`AGE_14`·`SERVICE`·`PRIVACY`·`THIRD_PARTY`)은 모두 포함, 선택 약관은 동의한 것만. 중복은 무시. 코드 목록은 `GET /api/v1/terms` (`terms.md`) |

오류: 형식 위반·`agreedTerms` 누락 `COMMON_400`, 필수 약관 누락 `TERMS_002`(400), 없는 약관 코드 `TERMS_001`(404), 아이디 중복 `AUTH_001`(409),
휴대폰 인증 실패(미인증·만료·번호 불일치·재사용) `AUTH_004`(400), 휴대폰 번호 중복 `AUTH_010`(409)

## POST /api/v1/auth/login — 고객·관리자 로그인

```json
{ "loginId": "customer1", "password": "test1234!" }
```

오류: 아이디 없음·비밀번호 불일치 `AUTH_002`(401), 비활성 회원 `AUTH_003`(403)

## POST /api/v1/auth/shops/signup — 매장 회원가입

`SHOP`의 필수 컬럼을 한 번에 넣어야 해서 계정과 매장 정보를 함께 받는다. 매장 정보 검증은 매장 수정 API(`shops.md`)와 같다(단, 가입 때 `phone`은 휴대폰 번호만 받는다). 대표 이미지는 가입 후 이미지 API로 올린다. 매장 약관 동의는 `SHOP_TERMS_AGREEMENT`에 같은 트랜잭션으로 저장한다. `201 Created`.

가입한 매장은 승인 대기(`PENDING`)로 저장되고 토큰을 발급하지 않는다. 관리자가 승인(`admin-shop-applications.md`)하면 `ACTIVE`가 되어 로그인할 수 있다.
응답 data: `{ "shopId": 10, "status": "PENDING" }`

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
  "phone": "01012345678",
  "description": "젤네일·케어 전문",
  "notice": "예약 시간 10분 전 도착해 주세요",
  "businessNumber": "숫자 10자리 사업자등록번호",
  "representativeName": "사업자등록증에 기재된 대표자명",
  "openedOn": "2020-01-01",
  "agreedTerms": ["SHOP_SERVICE", "SHOP_PRIVACY", "SHOP_BUSINESS_CHECK", "SHOP_SMS_NOTIFICATION"],
  "phoneVerificationId": "휴대폰 인증 API에서 받은 verificationId"
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
| phone | 필수, 휴대폰 번호(하이픈 유무 무관). `010-0000-0000` 형식으로 저장. 고객·다른 매장과 같은 번호여도 가입 가능 |
| agreedTerms | 필수(배열). 매장 약관 코드(`GET /api/v1/terms?target=SHOP`). 필수 매장 약관은 모두 포함, 선택 약관(`SHOP_SMS_NOTIFICATION`)은 동의한 경우만 |
| phoneVerificationId | 필수. `accountType: "shop"`으로 받은 인증 중 해당 phone으로 인증 완료한 ID. 가입 성공 시 1회 소비 |
| businessNumber | 필수, 숫자 10자리·체크섬 검사. 국세청 검증 후 SHOP.BUSINESS_NUMBER에 저장. 동일 번호로 매장 하나만 등록 가능 |
| representativeName | 필수, 100자 이하. 사업자등록증에 기재된 대표자명. 외국인 대표자는 등록된 영문명 |
| openedOn | 필수, YYYY-MM-DD, 미래일 불가. 사업자등록증의 개업연월일 |
| description, notice | 선택, 2000자 이하 |

오류: 형식 위반·없는 카테고리 `COMMON_400`, 아이디 중복 `AUTH_001`(409), 휴대폰 인증 실패 `AUTH_004`(400),
사업자 정보 불일치·휴업·폐업 `AUTH_007`(400), 국세청 조회 실패 `AUTH_008`(503), 사업자등록번호 중복 `AUTH_009`(409),
없는 약관·고객 약관 코드 `TERMS_001`(404), 필수 매장 약관 누락 `TERMS_002`(400)

## POST /api/v1/auth/shops/login — 매장 로그인

요청 형식은 고객 로그인과 같다. 응답의 `role`은 `OWNER`, `userId`·`shopId`는 매장 ID.

비밀번호가 맞은 뒤 `SHOP.STATUS`를 확인한다. 승인 대기(`PENDING`)·거절(`REJECTED`) 매장은 로그인할 수 없다.
`INACTIVE`는 노출 중지일 뿐이라 로그인해 매장을 관리할 수 있다.

오류: 아이디 없음·비밀번호 불일치 `AUTH_002`(401), 승인 대기 `AUTH_011`(403),
거절 `AUTH_012`(403, 사유가 있으면 메시지 끝에 `사유: {거절 사유}`를 붙인다)

## GET /api/v1/auth/shop-categories — 매장 가입용 카테고리

로그인 없이 조회한다.

```json
[ { "categoryId": 1, "name": "네일" }, { "categoryId": 5, "name": "바버" } ]
```

## GET /api/v1/auth/me — 현재 사용자

기존과 같다. 비로그인이면 `COMMON_401`.

## 가입 사전 확인 (UNNIE-173)

### POST /api/v1/auth/login-id/check

요청: `{ "loginId": "unnie01", "accountType": "customer" }`

`accountType`은 `customer` 또는 `shop`. 응답 data: `{ "available": true }`.
형식 오류는 `COMMON_400`. 이 결과는 사전 안내이며 최종 가입에서도 중복을 다시 검사한다.

### POST /api/v1/auth/phone/send

요청: `{ "phone": "01012345678", "accountType": "customer" }`

`accountType`은 `customer`(생략 시 기본값) 또는 `shop`. 고객 가입이면 이미 가입된 휴대폰 번호에 문자를 보내지 않고 `AUTH_010`(409)을 반환한다. 매장 가입(`shop`)은 번호 중복을 검사하지 않는다.

응답 data 예시:

```json
{
  "verificationId": "ce79983d-c4b3-4e64-b8eb-bbcd32206e17",
  "expiresAt": "2026-11-02T09:03:00+09:00",
  "resendAvailableAt": "2026-11-02T09:01:00+09:00",
  "verified": false
}
```

- 6자리 난수, 3분 만료. 인증번호는 API 응답에 포함하지 않고 해시로 보관한다.
- 재발송 60초 대기, 번호당 시간당 10회, 서버 전체 시간당 100회. 실패·응답 불명도 시도 횟수에 포함한다.
- 재발송하면 이전 인증은 무효화한다. 발송 실패·응답 불명을 성공으로 처리하지 않는다.
- 문자 발송에는 기존 `solapi.api-key`, `solapi.api-secret`, `solapi.from-number` 설정을 사용한다.
- 실제 문자 발송 검증에는 사람이 설정한 SOLAPI 계정·등록 발신번호·발송 잔액이 필요하다.

### POST /api/v1/auth/phone/verify

요청: `{ "verificationId": "발송 응답 ID", "code": "문자로 받은 6자리" }`

성공 시 발송 응답과 같은 형식이며 `verified: true`, `expiresAt`은 인증 완료부터 10분 후이다.
오입력 5회 이후 새 인증번호가 필요하다. 재확인으로 유효기간을 연장하지 않는다.

- 고객·매장 가입에 `phone`과 일치하는 인증 ID를 보낸다. 매장 가입은 휴대폰 인증과 사업자 정보 확인을 모두 한다.
- 인증 ID 누락·번호 변경·만료·미인증·재사용은 서버에서 차단한다.
- 가입 트랜잭션 커밋 때 인증을 소비하며, 롤백하면 유효시간 내 재시도가 가능하다.
- 오류: `AUTH_004` 인증 실패, `AUTH_005` 발송 제한, `AUTH_006` 발송 실패, `AUTH_010` 고객 휴대폰 번호 중복. 상세 메시지는 서버 응답을 표시한다.
- 고객은 휴대폰 번호당 한 계정만 가입할 수 있다. 발송과 최종 가입에서 검사하고 `APP_USER.PHONE` UNIQUE 제약으로도 막는다. 매장은 번호 중복을 허용한다(`SHOP.PHONE`에 UNIQUE 제약 없음).
- 현재 인증 상태·발송 제한은 **단일 서버 메모리**에 보관한다. 재시작 시 재인증이 필요하며 다중 서버 배포 전 공유 저장소로 교체해야 한다.
- 전화번호 소유 확인 기능이다. 대표자 본인 확인·사업자 조회는 이 API의 범위가 아니다.

## POST /api/v1/auth/business/verify — 사업자 정보 확인 (UNNIE-173)

요청:

```json
{
  "businessNumber": "숫자 10자리 사업자등록번호",
  "representativeName": "사업자등록증에 기재된 대표자명",
  "openedOn": "2020-01-01"
}
```

응답 data: `{ "verified": true }`. 정보 불일치·휴업·폐업은 `AUTH_007`,
조회 실패는 `AUTH_008`, 이미 가입된 번호는 `AUTH_009`이다.

- 번호 형식·체크섬·개업일·중복을 먼저 검사한다.
- 국세청 `POST /api/nts-businessman/v1/validate`에 `b_no`, `p_nm`, `start_dt(YYYYMMDD)`를 보낸다.
- `status_code=OK`, 대상 번호 일치, `valid=01`, `status.b_stt_cd=01`일 때만 통과한다.
- 공식 진위확인 응답에 상태조회 결과가 포함되므로 별도 상태조회 호출은 하지 않는다.
- HTTP 200이어도 불일치·누락·예상하지 못한 본문이면 성공으로 처리하지 않는다.
- 연결 제한 2초, 읽기 제한 4초. 실패 시 자동 재시도하지 않는다.
- 사전 확인 결과는 화면 안내용이다. 최종 매장 가입 시 입력한 값으로 국세청을 다시 조회한다.
- 화면에서 번호·대표자명·개업일을 변경하면 확인 상태를 초기화한다. 이전 입력에 대한 늦은 응답도 무시한다.
- 이 기능은 사업자 정보 일치와 계속사업 여부를 확인한다. 가입자가 실제 대표자인지를 인증하지 않는다.

### 인증키 설정 (사람이 직접 입력)

`backend/src/main/resources/application-local.yml.example`의 `nts` 항목을 참고하여
기존 `application-local.yml`에 `nts.service-key`를 추가하거나, 백엔드 실행 환경에
`NTS_SERVICE_KEY`를 설정한다. 공공데이터포털 **일반 인증키(Decoding)**를 사용한다.
로컬 설정 파일에 키를 직접 넣었다면 해당 값이 환경변수보다 우선한다.

키는 URI 변수로 인코딩하며 프론트로 전달하지 않는다. 키가 포함된 URL·원본 통신 예외·국세청
원본 응답을 로그에 남기지 않는다. 키 미설정·예시 값 `CHANGE_ME`는 조회 실패로 처리한다.
실제 키는 사람이 직접 입력·수정·삭제하며, Git에 커밋하지 않는다.

### 로컬 가짜 조회 (실제 사업자번호 없이 화면 확인)

`application-local.yml`의 `nts` 아래에 `fake: true`를 넣고 local 프로필로 실행하면 국세청을 호출하지 않는다.
local 프로필이 아니면 켜지지 않으며, 기동 시 경고 로그를 남긴다. 형식·체크섬·미래 개업일·중복 검사는 그대로 동작한다.

| 입력 | 결과 |
|---|---|
| 대표자명 `테스트 대표` + `1234567891`, `1111111119`, `4444444444`, `5555555553` 중 하나 | 통과 |
| 대표자명이 `테스트 대표`가 아님 | 정보 불일치 `AUTH_007` |
| `2222222227` (휴업), `3333333336` (폐업) | 휴업·폐업 `AUTH_007` |

개업일은 미래만 아니면 아무 날짜나 된다. 통과 번호는 한 번 가입하면 중복(`AUTH_009`)이 되므로 매장마다 다른 번호를 쓴다.

### 스키마 변경과 기존 DB 적용

- 원본 DDL: `docs/erd/01_schema_ddl.sql`의 `SHOP.BUSINESS_NUMBER VARCHAR2(10 CHAR)`.
- 중복 제약: `UK_SHOP_BUSINESS_NUMBER`. 사전 확인 이후 동시 요청도 DB에서 차단한다.
- 기존 매장·시연 데이터는 `NULL`을 유지한다. 임의의 사업자번호로 채우지 않는다.
- 기존 Oracle DB에는 배포 전 PR에 기록한 `SHOP.BUSINESS_NUMBER` 추가 SQL을 **한 번** 적용한다.
- 신규 H2는 원본 DDL에서 생성한다. 생성된 `schema.sql`은 직접 수정하지 않는다.
- 대표자명·개업일은 조회 입력으로만 사용하고 새 DB 컬럼에 저장하지 않는다.
- 같은 사업자번호의 지점 추가는 현재 지원하지 않는다. 계정당 매장 하나 정책에 따른다.

공식 기준: [공공데이터포털 국세청 API](https://www.data.go.kr/tcs/dss/selectApiDataDetailView.do?publicDataPk=15081808),
[공식 Swagger 명세](https://infuser.odcloud.kr/api/stages/28493/api-docs?1728017570963).
신규 개업 정보 반영에는 1~2일 걸릴 수 있다.

## 테스트 계정 (local 초기 데이터)

비밀번호는 모두 `test1234!`. 로컬·시연 전용이다.

| 아이디 | 로그인 API | 역할 |
|---|---|---|
| customer1, customer2 | `/auth/login` | CUSTOMER |
| admin | `/auth/login` | ADMIN |
| owner1 (매장 1), owner2 (매장 2) | `/auth/shops/login` | OWNER |
