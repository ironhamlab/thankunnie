# 시술 메뉴 관리 (A-04 / UNNIE-32, A-06 / UNNIE-44)

사장님은 본인 매장의 시술을 등록·조회·수정하고 활성 상태를 변경한다.
현재 사용자와 매장 ID는 B-01의 `GET /api/v1/auth/me`와 `@LoginUser AuthUser`를 사용한다.
모든 JSON 응답은 `ApiResponse<T>` 형식이다.

## API

기본 경로: `/api/v1/shops/{shopId}/treatments`

| 메서드 | 경로 | 동작 | 성공 |
|---|---|---|---|
| GET | 기본 경로 | 본인 매장 전체 시술 목록 (비활성 포함, ID 내림차순) | 200 |
| GET | `/categories` | 선택 가능한 CATEGORY 목록 (이름 오름차순) | 200 |
| GET | `/{treatmentId}` | 본인 매장 시술 단건 | 200 |
| POST | 기본 경로 | 시술 등록 | 201 |
| PUT | `/{treatmentId}` | 기본 정보 전체 수정 | 200 |
| PATCH | `/{treatmentId}/status` | 활성 상태 변경 | 200 |

등록·수정 요청:

```json
{
  "name": "젤 네일",
  "description": "기본 케어 포함",
  "regularPrice": 50000,
  "price": 40000,
  "lastMinutePrice": 30000,
  "categoryId": 1,
  "durationMinutes": 60,
  "conditions": {
    "treatmentType": "MODEL",
    "participationFee": 0,
    "modelCondition": "탈색 이력이 없는 분",
    "photoNotice": "전후 사진 촬영 및 포트폴리오 사용 안내"
  }
}
```

- 시술명: 공백만 입력 불가, 최대 100자. 앞뒤 공백을 제거해 저장한다.
- 설명: 선택, 최대 2,000자.
- 정상가: 양의 정수, 100원 단위. 기존 가격 계산의 Integer 범위에 맞춰 최대 2,147,483,600원.
- 카테고리: CATEGORY에 존재하는 ID 필수. 매장 카테고리와 독립적으로 선택한다.
- 소요 시간: 30~9,990분, 30분 배수.
- 신규 시술: NORMAL / ACTIVE. 판매가와 초임박가는 필수 입력이며 요청한 값으로 저장한다.
- 등록·수정 모두 양의 정수, 100원 단위, 초임박가 ≤ 판매가 ≤ 정상가를 검사한다. 같은 가격이면 할인 없음.
- 화면의 판매가·초임박가 기본값은 정상가이며, 수정 화면은 서버에 저장된 가격을 표시한다.
- `conditions`를 생략하거나 null로 보내면 신규 등록은 NORMAL, 수정은 기존 목적·모델 조건을 보존한다.
- `conditions`를 제공하면 목적·모델 조건 전체를 함께 저장한다. `treatmentType`은 NORMAL 또는 MODEL 필수다.
- MODEL: 참가비 필수, 0~2,147,483,600원, 100원 단위(무료 0원 허용).
  참여 조건(최대 2,000자)과 촬영 안내(최대 1,000자)는 공백만 입력할 수 없고 앞뒤 공백을 제거해 저장한다.
- NORMAL: 참가비는 null, 참여 조건·촬영 안내는 null 또는 공백이어야 하며 기존 모델 정보를 비운다.
- 참가비는 모집 안내 금액이며 기존 판매가·예약금·결제 계산에는 사용하지 않는다.
  일반·모델 모두 기존 시술 판매가 검증은 유지한다.
- 기본 정보 수정은 직원, 준비 안내, 이미지, 활성 상태를 변경하지 않는다.
- 기존 예약의 적용 가격·예약금·시작/종료 시각은 변경하지 않는다.

상태 변경 요청: `{"status":"INACTIVE"}` 또는 `{"status":"ACTIVE"}`.
물리 삭제 API는 제공하지 않는다.

시술 응답 필드: `treatmentId`, `shopId`, `name`, `description`, `regularPrice`, `price`, `lastMinutePrice`,
`categoryId`, `categoryName`, `durationMinutes`, `status`, `treatmentType`, `participationFee`,
`modelCondition`, `photoNotice`, `createdAt`, `updatedAt`.
시각은 `+09:00` 오프셋이다.
카테고리 응답 항목: `categoryId`, `name`.

## 오류

| 코드 | 상태 | 조건 |
|---|---|---|
| COMMON_400 | 400 | 입력 검증 실패, 없는 카테고리, 잘못된 상태 |
| VACANCY_007 | 400 | 판매가·초임박가가 양의 100원 단위가 아니거나 판매가가 정상가 초과 |
| VACANCY_008 | 400 | 초임박가가 판매가 초과 |
| COMMON_401 | 401 | 비로그인 |
| COMMON_403 | 403 | 사장님이 아니거나 경로의 매장이 본인 매장이 아님 |
| COMMON_404 | 404 | 없는 매장/시술 또는 본인 매장 경로에 다른 매장 시술 ID 사용 |

## 화면 및 로컬 확인

- 화면: `/design/owner/menus`. 시연용 브라우저 메모리 메뉴 대신 서버 응답을 사용한다.
- 화면 → `useTreatmentStore` → `treatmentApi` → `axiosInstance` 순서로 요청한다.
- 인증은 기존 공통 방식을 사용한다. 실제 로그인 연동 전 로컬 확인은 B-01의 `X-Test-User: OWNER_1` 헤더를 요청에 부여하는 개발 도구를 사용한다. 운영에서는 테스트 헤더 인증이 동작하지 않는다.
- OWNER_1의 SHOP_ID=1 매장과 CATEGORY 초기 데이터가 있어야 한다. 초기 데이터가 없으면 매장 없음/카테고리 없음 안내가 표시된다.
- 공통 인증·CORS·라우터 파일을 수정하거나 개별 임시 인증 방식을 추가하지 않았다.
- 다른 사장님 페이지의 빈자리·예약 데이터는 기존 시연용 구현이다. 해당 업무 API 연동은 각 담당 이슈 범위다.

## 기존 DB 변경

A-06 적용 전 `docs/erd/migrations/UNNIE-44-treatment-model-conditions.sql`을 기존 DB에 1회 적용한다.
TREATMENT에 nullable NUMBER(10) PARTICIPATION_FEE 컬럼을 추가한다.
기존 모델의 참가비는 임의로 채우지 않으므로 모델 조건을 다음에 저장할 때 사장님이 입력한다.
Oracle 21c XE에서 마이그레이션 및 validate 기동 검증이 별도로 필요하다.

새 DB는 `docs/erd/01_schema_ddl.sql`을 사용한다.
기존 Oracle 21c XE에는 아래 SQL 파일을 적용해야 한다.

`docs/erd/migrations/UNNIE-32-treatment-category.sql`

SQL은 CATEGORY_ID를 추가하고 기존 시술을 소속 매장의 카테고리로 채운 뒤 NOT NULL과 FK를 적용한다.
카테고리 마스터는 기존 CATEGORY를 공유하고 선택값은 시술별로 저장한다.
Oracle에서 실제 적용한 결과는 별도 검증이 필요하다.

## A-06 검증 결과

- `backend/`: `.\gradlew.bat --no-daemon classes test` 성공, 전체 282개 테스트 통과.
- 모델 조건 수정 및 최대 길이 경계값 검증 보강 후
  `.\gradlew.bat --no-daemon test --tests 'com.ucamp.app.domain.treatment.*'` 재실행 성공.
- `frontend/`: `npm run lint`, `npm run build` 성공.
- `.\gradlew.bat --no-daemon bootRun`에 local 프로필, 임시 H2 Oracle 모드 URL,
  포트 18080 및 scheduling 비활성 옵션을 전달해 `validate` 기동 성공.
  실제 HTTP 등록·재조회로 모델 목적, 무료 참가비, 참여 조건, 촬영 안내 저장 확인.
- 검증 프로세스에만 `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:/UCAMP/Final/thankunnie/backend/build/socket-temp` 지정.
- 브라우저 화면 조작 및 Oracle 21c XE의 마이그레이션 적용은 미검증.

## A-04 당시 검증 범위 및 기존 차단 사항

- `backend/`: `.\gradlew.bat classes` 컴파일 확인.
- `backend/`: `.\gradlew.bat test --tests 'com.ucamp.app.domain.treatment.*'` 22개 통과.
  시술·매장 엔티티만 등록한 테스트 구성에서 공통 인증·예외 처리와 MockMvc 요청 → Service → JPA → H2 저장을 검증했다.
  `ddl-auto=validate`와 원본 DDL을 그대로 사용한다.
- `frontend/`: `npm run lint`, `npm run build` 통과.
- 브라우저: API 모의 응답으로 등록·수정·비활성화·재조회, 입력 검증, 저장 실패 시 입력 유지,
  로그인 오류, 카테고리 없음, 데스크톱·모바일 폼 표시 확인.
- 기존 `NOTIFICATION_SUBSCRIPTION` 엔티티는 `CATEGORY_ID`를 요구하지만 원본 DDL에는 `CATEGORY_NAME`만 있다.
  이 불일치로 전체 앱 기동과 전체 테스트는 실패한다. 최종 `.\gradlew.bat --no-daemon test` 결과는 165개 중 140개 통과, 25개 실패다.
  사용자 요청에 따라 알림 담당자의 수정 전까지 유지한다.
  이번 변경은 알림 구독 스키마를 수정하지 않는다.
- 실제 서버와 브라우저 간 전체 연동 및 Oracle 마이그레이션 적용 결과는 아직 검증하지 못했다.
- 설치된 Windows JDK의 Unix 소켓 임시 경로 문제를 피하기 위해 검증 프로세스에만
  `JAVA_TOOL_OPTIONS`의 `-Djdk.net.unixdomain.tmpdir`에 프로젝트 내부 `backend/build/socket-temp`의 절대 경로를 지정하고
  Gradle에 `--no-daemon`을 사용했다. 저장소 실행 설정은 변경하지 않았다.
