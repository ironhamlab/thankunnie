# 약관·동의 API (UNNIE-146)

> 기준: `backend/src/main/java/com/ucamp/app/domain/user/controller/TermsController.java`
> 응답은 공통 `ApiResponse` 형식이다. 오류 코드는 `error-codes.md`의 COMMON·TERMS 참고.

## 구조

| 테이블 | 역할 |
|---|---|
| `TERMS` | 약관 마스터. 코드·버전당 1행. 본문은 프론트에서 관리하고 제목·필수 여부만 둔다 |
| `USER_TERMS_AGREEMENT` | 회원 약관 동의. 회원·약관 버전당 1행 (`UK(USER_ID, TERMS_ID)`). `WITHDRAWN_AT` 은 철회 대비 자리로 MVP 에서는 항상 NULL |
| `SHOP_TERMS_AGREEMENT` | 매장 약관 동의. 매장·약관 버전당 1행 (`UK(SHOP_ID, TERMS_ID)`). 매장 가입 API 가 함께 저장한다 |

고객 약관:

| 코드 | 내용 | 필수 | 받는 시점 |
|---|---|---|---|
| `AGE_14` | 만 14세 이상 확인 | Y | 회원가입 |
| `SERVICE` | 서비스 이용약관 | Y | 회원가입 |
| `PRIVACY` | 개인정보 수집·이용 (아이디·이름·전화번호, 예약 확인·매장 연락 목적) | Y | 회원가입 |
| `THIRD_PARTY` | 개인정보 제3자 제공 (예약한 매장에 이름·전화번호 제공) | Y | 회원가입 |
| `SMS_NOTIFICATION` | 관심 시간 알림 문자 수신 | N | 회원가입, 또는 희망 시간이 있는 구독 등록·수정 시 동의 모달 |
| `MODEL_PHOTO` | 초보 시술 촬영·결과물 활용 | N | 첫 초보 시술 예약 시 동의 모달 (가입 화면에는 노출하지 않음) |

매장 약관 (모두 매장 가입 때 받는다):

| 코드 | 내용 | 필수 |
|---|---|---|
| `SHOP_SERVICE` | 매장 서비스 이용약관 | Y |
| `SHOP_PRIVACY` | 매장 개인정보 수집·이용 (사업자·대표자·휴대폰 번호·주소) | Y |
| `SHOP_BUSINESS_CHECK` | 국세청 사업자 정보 조회 | Y |
| `SHOP_SMS_NOTIFICATION` | 매장 알림 문자 수신 | N |

- 동의는 항상 코드별 **최신 버전** 기준이다. 약관을 개정해 같은 코드의 새 버전 행을 넣으면 이전 버전에만 동의한 회원은 미동의로 판단된다.
- 고객 약관과 매장 약관은 코드로 구분한다. 고객 가입에 매장 약관 코드, 매장 가입에 고객 약관 코드를 보내면 `TERMS_001`(404).
- 매장은 아래 동의 조회·동의 API 를 쓸 수 없다. 매장 동의는 매장 가입 때만 저장한다.
- 대상별 필수 약관이 하나도 등록되어 있지 않으면(운영 DB 에 TERMS 초기 데이터 누락) 해당 가입은 `COMMON_500`으로 실패한다. 동의 없이 가입되는 것을 막기 위함이다.
- 철회는 MVP 범위 밖이다. 문자 수신 중단은 구독 해제로 한다.

## GET /api/v1/terms — 약관 목록

로그인 없이 조회한다. 코드별 최신 버전만, 위 표의 코드 순서로 반환한다.
`target` 쿼리로 대상을 고른다: 생략하거나 `CUSTOMER`면 고객 약관, `SHOP`이면 매장 약관 (`GET /api/v1/terms?target=SHOP`).

```json
{
  "success": true,
  "data": [
    { "code": "AGE_14", "version": 1, "title": "만 14세 이상 확인", "required": true },
    { "code": "SMS_NOTIFICATION", "version": 1, "title": "관심 시간 알림 문자 수신 동의", "required": false }
  ],
  "error": null,
  "timestamp": "2026-10-30T22:40:00+09:00"
}
```

## GET /api/v1/users/me/terms-agreements — 내 약관 동의

고객만 조회한다. 최신 버전 약관 중 동의한 것만 반환한다.

```json
{
  "success": true,
  "data": [
    { "code": "AGE_14", "version": 1, "title": "만 14세 이상 확인", "required": true,
      "agreedAt": "2026-10-30T22:40:00+09:00" }
  ],
  "error": null,
  "timestamp": "2026-10-30T22:40:00+09:00"
}
```

오류: 비로그인 `COMMON_401`, 고객이 아님 `COMMON_403`

## PUT /api/v1/users/me/terms-agreements/{termsCode} — 약관 동의

최신 버전 약관에 동의한다. 이미 동의했으면 저장하지 않고 기존 동의를 그대로 반환한다(반복 요청 안전). 같은 고객의 동시 요청은 회원 잠금으로 줄 세워 한 건만 저장한다. 응답 `data` 는 내 약관 동의 항목 1건과 같은 형식이다. `200 OK`.

문자 수신·초보 시술 촬영 동의 모달에서 호출한 뒤, 막혔던 요청(구독 등록·예약 생성)을 다시 보낸다.

오류: 비로그인 `COMMON_401`, 고객이 아님 `COMMON_403`, 잘못된 코드 형식 `COMMON_400`, 등록되지 않은 약관 `TERMS_001`(404)

## 다른 도메인에서 동의 검사 (UNNIE-147)

아래 두 곳에 연결되어 있다.

`TermsAgreementService.requireAgreed(userId, code)` 를 호출한다. 동의하지 않았으면 `SMS_NOTIFICATION` 은 `TERMS_003`, `MODEL_PHOTO` 는 `TERMS_004`(409)를 던진다.

| 호출 위치 | 검사 |
|---|---|
| 구독 등록·수정 (`NotificationService.subscribe`, `updateSubscription`) | 등록: 희망 시간이 있을 때만 `requireAgreed(userId, SMS_NOTIFICATION)`. 희망 시간 없는 업장 찜은 문자가 나가지 않아 검사하지 않음. 수정: 찜(희망 시간 없음)에 희망 시간을 넣어 알림을 새로 켤 때만 검사하고, 이미 알림이 켜진 구독의 수정은 검사하지 않음(약관 도입 전 구독 포함). 대상·시간 검증이 동의 검사보다 먼저라 잘못된 요청은 400 으로 응답. 해제는 검사하지 않음 |
| 예약 생성 (`ReservationService.createReservation`, 초보 시술만) | `requireAgreed(userId, MODEL_PHOTO)`. 회원·매장 잠금 전에 검사 |
