# 시술 메뉴 관리 (A-04 / UNNIE-32, A-05 / UNNIE-44, A-06 / UNNIE-45)

매장은 본인 매장의 시술을 등록·조회·수정하고 활성 상태를 변경한다.
현재 사용자와 매장 ID는 B-01의 `GET /api/v1/auth/me`와 `@LoginUser AuthUser`를 사용한다.
모든 JSON 응답은 `ApiResponse<T>` 형식이다.

## API

기본 경로: `/api/v1/shops/{shopId}/treatments`

| 메서드 | 경로 | 동작 | 성공 |
|---|---|---|---|
| GET | 기본 경로 | 본인 매장 전체 시술 목록 (비활성 포함, ID 내림차순) | 200 |
| GET | `?status=ACTIVE` | 신규 빈자리에서 선택 가능한 활성 시술만 조회 | 200 |
| GET | `/{treatmentId}` | 본인 매장 시술 단건 | 200 |
| POST | 기본 경로 | 시술 등록 | 201 |
| PUT | `/{treatmentId}` | 기본 정보 전체 수정 | 200 |
| PATCH | `/{treatmentId}/status` | 활성 상태 변경 | 200 |
| DELETE | `/{treatmentId}` | 시술 삭제 (DELETED 소프트 삭제) | 204 |

등록·수정 요청:

```json
{
  "name": "젤 네일",
  "description": "기본 케어 포함",
  "regularPrice": 50000,
  "price": 40000,
  "lastMinutePrice": 30000,
  "durationMinutes": 60,
  "conditions": {
    "treatmentType": "MODEL",
    "modelCondition": "탈색 이력이 없는 분",
    "photoNotice": "전후 사진 촬영 및 포트폴리오 사용 안내"
  }
}
```

- 시술명: 공백만 입력 불가, 최대 100자. 앞뒤 공백을 제거해 저장한다.
- 설명: 선택, 최대 2,000자.
- 정상가: 양의 정수, 100원 단위. 기존 가격 계산의 Integer 범위에 맞춰 최대 2,147,483,600원.
- 시술의 카테고리는 소속 매장의 카테고리를 따른다. 시술별 카테고리 선택은 제공하지 않는다.
- 소요 시간: 30~9,990분, 30분 배수.
- 신규 시술: `conditions`를 생략하면 기본 유형은 NORMAL이며, `conditions.treatmentType`에 MODEL을 지정하면 MODEL로 등록한다. 상태는 ACTIVE다. 판매가와 초임박가는 필수 입력이며 요청한 값으로 저장한다.
- 등록·수정 모두 양의 정수, 100원 단위, 초임박가 ≤ 판매가 ≤ 정상가를 검사한다. 같은 가격이면 할인 없음.
- 화면의 판매가·초임박가 기본값은 정상가이며, 수정 화면은 서버에 저장된 가격을 표시한다.
- `conditions`를 생략하거나 null로 보내면 신규 등록은 NORMAL, 수정은 기존 목적·초보 시술 조건을 보존한다.
- `conditions`를 제공하면 목적·초보 시술 조건 전체를 함께 저장한다. `treatmentType`은 NORMAL 또는 MODEL 필수다.
- MODEL: 참여 조건(최대 2,000자)과 촬영 안내(최대 1,000자)는 필수이며 공백만 입력할 수 없고 앞뒤 공백을 제거해 저장한다.
- NORMAL: 참여 조건·촬영 안내는 null 또는 공백이어야 하며 기존 초보 시술 정보를 비운다.
- 별도 참가비 입력·저장은 하지 않는다. 일반·초보 시술 모두 기존 시술 판매가 검증과 예약금 정책을 사용한다.
  예약 시 서버가 일반·초임박 구간에 해당하는 실제 적용 판매가와 20,000원 중 낮은 금액을 계산해
  `RESERVATION.DEPOSIT`에 저장한다. 예: 판매가 15,000원 → 예약금 15,000원, 판매가 30,000원 → 예약금 20,000원.
- 기본 정보 수정은 직원, 준비 안내, 이미지, 활성 상태를 변경하지 않는다.
- 기존 예약의 적용 가격·예약금·시작/종료 시각은 변경하지 않는다.

상태 변경 요청: `{"status":"INACTIVE"}` 또는 `{"status":"ACTIVE"}`. `DELETED`는 400으로 거절한다.

## 시술 삭제 (UNNIE-235)

- `DELETE /{treatmentId}`는 예약이 시술을 FK로 참조하므로 행을 지우지 않고 `STATUS`를 `DELETED`로 바꾼다.
- 삭제된 시술은 관리 목록·단건 조회·수정·상태 변경·이미지 API에서 404로 취급하고, 고객 화면은 기존처럼 ACTIVE만 노출한다.
- 같은 트랜잭션에서 대표 이미지 URL을 비우고 커밋 후 이미지 파일을 정리한다.
- 기존 예약의 상태·가격·시각은 변경하지 않는다. `STATUS` 컬럼(VARCHAR2(10), CHECK 제약 없음)을 그대로 사용하므로 스키마 변경은 없다.
- 목록의 `status=DELETED` 필터는 400으로 거절한다.

## 시술 비활성화 (A-05)

- `PATCH /{treatmentId}/status`에 `{"status":"INACTIVE"}`를 보내면 시술 행의 상태만 변경한다.
- 기본 목록과 단건 조회는 비활성 시술도 반환한다. 관리 목록과 기존 이력에서 계속 표시할 수 있다.
- 목록의 선택적 `status` 파라미터는 `ACTIVE` 또는 `INACTIVE`를 받는다. 생략하면 기존 전체 목록을 반환하고,
  그 외 값은 `COMMON_400`으로 거절한다. 상태 필터에도 인증 및 본인 매장 권한 검사를 적용한다.
- 신규 빈자리 등록 화면과 목록 내 등록 폼은 `?status=ACTIVE` 결과를 사용한다.
  시연용 고정 메뉴는 신규 선택 후보에서 제외한다. 저장 직전에 활성 목록을 다시 조회하고
  비활성화되거나 삭제된 후보는 저장을 거절한다. 조회 실패 시에도 저장하지 않는다.
- 비활성화는 기존 빈자리, 예약 상태, 가격, 예약금, 시작·종료 시각을 변경하거나 삭제하지 않는다.
  프론트엔드의 기존 빈자리와 메뉴 스냅샷도 보존한다.
- `ACTIVE`로 재활성화하면 신규 선택 목록에 다시 나타난다. 같은 상태로 반복 변경할 수 있다.
- 시술 선택은 서버와 연결되지만 빈자리 생성 자체는 아직 브라우저 메모리의 시연용 구현이다.
  실제 빈자리 생성 API 연결 시 해당 쓰기 트랜잭션에서도 시술 상태를 검증해야 한다.
  현재의 저장 직전 재조회만으로 서버 저장과 상태 변경 사이의 동시성을 보장하지는 않는다.
- 스키마 및 공통 인증·예외·설정 파일 변경은 없다.

시술 응답 필드: `treatmentId`, `shopId`, `name`, `description`, `regularPrice`, `price`, `lastMinutePrice`,
`durationMinutes`, `status`, `treatmentType`,
`modelCondition`, `photoNotice`, `imageUrl`, `createdAt`, `updatedAt`.
시각은 `+09:00` 오프셋이다.

## 오류

| 코드 | 상태 | 조건 |
|---|---|---|
| COMMON_400 | 400 | 입력 검증 실패, 잘못된 상태 |
| VACANCY_007 | 400 | 판매가·초임박가가 양의 100원 단위가 아니거나 판매가가 정상가 초과 |
| VACANCY_008 | 400 | 초임박가가 판매가 초과 |
| COMMON_401 | 401 | 비로그인 |
| COMMON_403 | 403 | 매장이 아니거나 경로의 매장이 본인 매장이 아님 |
| COMMON_404 | 404 | 없는 매장/시술 또는 본인 매장 경로에 다른 매장 시술 ID 사용 |

## 화면 및 로컬 확인

- 화면: `/owner/menus`. 시연용 브라우저 메모리 메뉴 대신 서버 응답을 사용한다.
- 화면 → `useTreatmentStore` → `treatmentApi` → `axiosInstance` 순서로 요청한다.
- 인증은 기존 공통 방식을 사용한다. 실제 로그인 연동 전 로컬 확인은 B-01의 `X-Test-User: OWNER_1` 헤더를 요청에 부여하는 개발 도구를 사용한다. 운영에서는 테스트 헤더 인증이 동작하지 않는다.
- OWNER_1의 SHOP_ID=1 매장 초기 데이터가 있어야 한다. 초기 데이터가 없으면 매장 없음 안내가 표시된다.
- 공통 인증·CORS·라우터 파일을 수정하거나 개별 임시 인증 방식을 추가하지 않았다.
- 다른 매장 페이지의 빈자리·예약 데이터는 기존 시연용 구현이다. 해당 업무 API 연동은 각 담당 이슈 범위다.

## 기존 DB 변경

A-06은 기존 목적·참여 조건·촬영 안내 컬럼을 사용하므로 추가 스키마 변경과 마이그레이션은 없다.

새 DB는 `docs/erd/01_schema_ddl.sql`을 사용하며 `TREATMENT.CATEGORY_ID`를 생성하지 않는다.
기존 Oracle DB에서 `TREATMENT.CATEGORY_ID`가 있으면 기존 버전 인스턴스를 모두 중지한 유지보수 시간에 아래 순서로 제거하고, 새 버전을 기동한다. 기존 버전의 Treatment 매핑을 사용하는 조회·등록·수정이 실행되는 동안에는 컬럼을 제거하지 않는다. `UNNIE-32-treatment-category.sql`을 적용한 DB와 해당 컬럼을 포함한 이전 전체 DDL로 생성한 DB가 모두 대상이다.

1. 대상 스키마의 `USER_TAB_COLUMNS`에서 `TREATMENT.CATEGORY_ID`, `USER_CONSTRAINTS`에서 `FK_TREATMENT_CATEGORY` 존재 여부를 확인한다.
2. 제약이 존재하면 `ALTER TABLE TREATMENT DROP CONSTRAINT FK_TREATMENT_CATEGORY;`를 실행한다.
3. 컬럼이 존재하면 `ALTER TABLE TREATMENT DROP COLUMN CATEGORY_ID;`를 실행한다.
4. 두 객체가 제거된 것을 확인한 뒤 새 버전을 기동한다. 컬럼과 제약이 없는 DB에서는 제거 SQL을 실행하지 않는다.

Oracle DDL은 자동 커밋되므로 실행 전 대상 스키마와 백업을 확인한다.
매장 카테고리는 `SHOP.CATEGORY_ID`를 계속 사용한다. Oracle에서 실제 적용한 결과는 별도 검증이 필요하다.

## A-06 초기 구현 당시 검증 결과

- `backend/`: `.\gradlew.bat --no-daemon classes test` 성공, 전체 282개 테스트 통과.
- 초보 시술 조건 수정 및 최대 길이 경계값 검증 보강 후
  `.\gradlew.bat --no-daemon test --tests 'com.ucamp.app.domain.treatment.*'` 재실행 성공.
- `frontend/`: `npm run lint`, `npm run build` 성공.
- `.\gradlew.bat --no-daemon bootRun`에 local 프로필, 임시 H2 Oracle 모드 URL,
  포트 18080 및 scheduling 비활성 옵션을 전달해 `validate` 기동 성공.
  실제 HTTP 등록·재조회로 초보 시술 목적, 참여 조건, 촬영 안내 저장 확인.
- 검증 프로세스에만 `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:/UCAMP/Final/thankunnie/backend/build/socket-temp` 지정.
- 브라우저 화면 조작 및 Oracle 21c XE의 마이그레이션 적용은 미검증.

## 검증 결과 (A-05 / UNNIE-44, 2026-10-26)

- `backend/`: `.\gradlew.bat --no-daemon classes test` 통과. 전체 286개, 실패 0개.
  시술 테스트는 통합 30개와 엔티티 9개를 포함한다.
- 추가 통합 테스트: 활성 목록 제외·재활성화, 관리 목록과 상세 유지, 인증·본인 매장 검사,
  잘못된 상태 및 빈 목록, 반복 비활성화 시 빈자리와 5개 상태의 예약 행 전체 보존.
- `frontend/`: `npm run lint`, `npm run build` 통과.
- `frontend/`: `node --test src/pages/design/useDesignStore.test.js` 2개 통과.
  비활성 시술 저장 거절과 기존 빈자리·예약·메뉴 스냅샷 보존을 검증했다.
- 이번 작업에서 실제 브라우저의 서버 연동 및 Oracle 실행은 검증하지 않았다.
- 이전 A-04 검증 당시 알림 구독 스키마 불일치로 전체 테스트가 실패했으나,
  현재 dev를 반영한 이번 검증에서는 전체 테스트가 통과했다.
- 설치된 Windows JDK의 Unix 소켓 임시 경로 문제를 피하기 위해 검증 프로세스에만
  `JAVA_TOOL_OPTIONS`의 `-Djdk.net.unixdomain.tmpdir`에 프로젝트 내부 `backend/build/socket-temp`의 절대 경로를 지정하고
  Gradle에 `--no-daemon`을 사용했다. 저장소 실행 설정은 변경하지 않았다.


## 대표 이미지 API-116·API-117 (UNNIE-46)

| 메서드·경로 | 용도 | 성공 응답 |
| --- | --- | --- |
| `PUT /api/v1/shops/me/treatments/{treatmentId}/image` | 본인 매장 시술 대표 이미지 업로드·교체 | 200, `ApiResponse<TreatmentImageResponse>` |
| `DELETE /api/v1/shops/me/treatments/{treatmentId}/image` | 본인 매장 시술 대표 이미지 삭제 | 204, 본문 없음 |
| `GET /api/v1/shops/{shopId}/treatments/{treatmentId}/images/{fileName}` | 현재 대표 이미지 공개 조회 | 200, JPEG·PNG 바이너리 |

- `me`는 B-01 인증 주체의 shopId를 사용한다. 비로그인은 401, 고객·관리자는 403이며 다른 매장의 시술 또는 존재하지 않는 시술은 404다.
- PUT은 `multipart/form-data`의 `file` 필드로 JPEG·PNG 1개를 전송한다. 최대 5MiB·1,600만 화소이며 실제 내용까지 검사한다. 누락·빈 파일·중복 파일·잘못된 형식은 400이다.
- 업로드 응답 data는 `{"treatmentId":9701,"imageUrl":"/api/v1/shops/1/treatments/9701/images/{서버 파일명}.png"}`이다.
- 시술 목록·상세 응답의 `imageUrl`에도 현재 대표 이미지 경로를 반환한다.
- 현재 이미지만 공개 조회하고 교체·삭제된 URL은 404를 반환한다. 이미지가 없어도 DELETE는 204다.
- 조회 응답은 매장 이미지와 같은 `Cache-Control: max-age=31536000, public, immutable`이다. 이미 받은 브라우저는 교체·삭제된 옛 URL을 캐시에서 열 수 있다(최대 1년).
- 매장 이미지와 같은 S3/로컬 저장소·검증·삭제 실패 재시도를 사용한다. DB 커밋 후 이전 파일을 정리하고 롤백 시 새 파일을 정리한다. 파일 저장 실패 시 기존 이미지는 유지한다.
- 파일 검증과 업로드는 DB 트랜잭션 밖에서 수행한다. 업로드 후 짧은 쓰기 트랜잭션에서 새 파일의 롤백 정리를 먼저 등록하고 SHOP 행 잠금과 시술 재조회 후 URL을 변경한다. 트랜잭션 시작·잠금·재조회·커밋 실패 시 새 파일을 정리한다. 같은 매장의 이미지 URL 변경은 SHOP 행 잠금으로 직렬화한다. 시술 메뉴 변경은 변경된 컬럼만 UPDATE하여 이미지 URL을 덮어쓰지 않는다.
- 기존 TREATMENT.IMAGE_URL 컬럼을 사용하므로 추가 스키마 변경은 없다. 저장소 설정은 [매장 이미지 명세](shops.md)를 따른다.
