# 운영 환경 실행 안내

## 사전 준비

- 서버에 Docker Engine과 Docker Compose를 설치한다.
- `unnie-network` Docker 네트워크를 생성한다.
- `.env.example`을 참고해 서버용 `.env`를 작성한다.
- 실제 비밀번호와 `.env`는 Git에 커밋하지 않는다.

## Oracle 최초 구성

1. `compose.infra.yml`로 Oracle을 실행한다.
2. Oracle이 healthy 상태인지 확인한다.
3. XEPDB1에 `.env`의 APP_USER 계정으로 접속한다.
4. `docs/erd/01_schema_ddl.sql`을 적용한다.
5. DDL 오류가 없는지 확인한 뒤
   `docs/erd/02_init_data_dml.sql`을 적용하고 COMMIT한다.
6. 이후 백엔드를 실행한다.

APP_USER가 SCOTT이면 서버의 SCOTT 계정에 적용한다.
로컬 DB와 서버 DB의 비밀번호는 각각 해당 환경 설정을 사용한다.

백엔드의 ddl-auto는 validate이며 테이블을 자동 생성하지 않는다.
기존 서버 DB에 이미 SQL을 적용했다면 전체 스크립트를 재실행하지 않는다.
최초 초기화 이후 `.env`만 수정해도 DB 비밀번호는 변경되지 않는다.

## 데이터 보존

Oracle 데이터는 `unnie-oracle-data` 볼륨에 보관한다.
일반 앱 배포에서는 Oracle 볼륨을 삭제하지 않는다.
`docker compose down -v`는 DB 데이터를 삭제할 수 있으므로 사용하지 않는다.

## HTTPS 적용 전 제한

현재 앱 Compose는 HTTP 검증용이다.
HTTPS 적용 전에는 EC2 보안 그룹의 80번 접근을 관리자 IP로 제한하고
테스트 계정만 사용한다.

공개 사용 전 HTTPS 인증서와 HTTP→HTTPS 리다이렉트를 구성한다.