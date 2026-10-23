# A-03 매장 관리 API (#27)

현재 구현 범위는 백엔드 매장 정보 관리이다. 로그인/계정 생성 및 화면 연결은 별도 기능이다.
인증된 OWNER 계정에 이미 할당된 shopId로 정보를 등록한다. 요청 본문으로 매장 ID나 소유자를 지정하지 않는다.
실제 인증 도입 전 local/test에서는 X-Test-User: OWNER_1 또는 OWNER_2를 사용한다.

| 메서드 | 경로 | 성공 | 동작 |
| --- | --- | --- | --- |
| POST | /api/v1/shops | 201 | 본인 매장 최초 등록 |
| GET | /api/v1/shops/{shopId} | 200 | 본인 매장 조회 |
| PUT | /api/v1/shops/{shopId} | 200 | 본인 매장 전체 수정 |

등록/수정 본문:

```json
{
  "name": "매장 이름",
  "description": "매장 소개",
  "address": "서울특별시 중구 세종대로",
  "addressDetail": "1층",
  "latitude": 37.5665,
  "longitude": 126.978
}
```

name(최대 100자), address(최대 255자), latitude, longitude는 필수다.
description(최대 1000자), addressDetail(최대 255자)은 선택이며 PUT에서 생략하면 null로 변경한다.
위도는 -90~90, 경도는 -180~180이며 소수점은 최대 7자리다. 주소를 좌표로 변환하는 외부 API는 호출하지 않는다.
성공 응답은 ApiResponse로 감싸며 data에 shopId와 위 본문의 필드를 반환한다.

- 미인증: COMMON_401 / 401
- 고객 또는 타 매장 소유자: COMMON_403 / 403 (DB 조회 전에 검사)
- 미등록 본인 매장: COMMON_404 / 404
- 입력 검증 실패 또는 중복 최초 등록: COMMON_400 / 400

## DB 적용 및 검증

기존 DB에는 docs/erd/01_schema_ddl.sql의 CREATE TABLE SHOP을 최초 1회 수동 적용한다.
SHOP_ID는 인증에서 할당하므로 시퀀스가 없다. VERSION은 JPA 낙관적 잠금용이다.
로컬 H2에도 기동 전에 같은 테이블을 준비해야 하며 ddl-auto는 validate를 유지한다.
테스트는 src/test/resources/schema.sql로 H2 테이블을 준비한다.
Oracle DDL은 Oracle 21c XE에서 직접 적용 및 validate 기동 확인이 추가로 필요하다.
