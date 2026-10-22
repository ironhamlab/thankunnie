# AGENTS.md — AI 코딩 에이전트 작업 지침

> 이 파일은 Claude Code, Copilot 등 AI 코딩 에이전트가 이 저장소에서 작업할 때 반드시 먼저 읽어야 하는 지침임.
> 사람 개발자용 상세 규칙은 `docs/git-guide.md`, `docs/convention/BACKEND_CONVENTION.md`, `docs/convention/FRONTEND_CONVENTION.md`를 참조하며, 본 문서와 충돌 시 **본 문서의 금지 사항이 우선**함.

---

## 1. 프로젝트 개요

- **프로젝트명**: thankunnie (3주 단기 풀스택 수료 프로젝트, 6인 팀)
- **구조**: 모노리포 (`backend/` + `frontend/` + `docs/` 단일 저장소)
- **팀 구성**: 총 6인 전원 풀스택, 팀장(Repo Owner) 1명, 프론트엔드 파트장 1명, 백엔드 파트장 1명.
- **기본 브랜치**: `dev` (PR 기본 타겟). `main`은 스프린트 종료/최종 발표/필수적인 공통사항일 시에만 병합.

| 영역 | 스택 | 비고 |
|---|---|---|
| Back-end | Java 21, Spring Boot 3.5, Spring Data JPA, Gradle | 패키지 루트 `com.ucamp.app` |
| Front-end | React 19, JavaScript (ES6+), Vite, React Router v6, Zustand, Axios | 개발 포트 5173 |
| 개발·테스트 DB | H2 | 프로파일별 연결 설정 |
| 운영 DB | Oracle 21c XE | 스키마 기준: `docs/erd/01_schema_ddl.sql` |
| 로컬 Oracle 확인용 | Docker Compose의 Oracle Free | `localhost:1521`, 서비스명 `FREEPDB1`, 계정 `SCOTT / TIGER` |
| VCS | Git 2.54+, GitHub (Rulesets로 `main`/`dev` 보호) | Squash and merge 원칙 |

- Docker Oracle Free는 맥 사용자를 포함한 팀원의 로컬 Oracle 확인용으로 사용함.
- Oracle Free와 Oracle 21c XE는 버전이 다르므로, 버전 차이에 영향을 받는 기능은 Oracle 21c XE에서도 확인함.

---

## 2. 저장소 레이아웃

```text
project-root/
├── backend/                     # Spring Boot (Gradle). 상세: docs/convention/BACKEND_CONVENTION.md
│   ├── src/main/java/com/ucamp/app/
│   │   ├── Application.java
│   │   ├── config/
│   │   ├── common/
│   │   └── domain/
│   │       └── {도메인}/
│   │           ├── controller/
│   │           ├── service/
│   │           ├── repository/
│   │           ├── entity/
│   │           └── dto/
│   │               ├── request/
│   │               └── response/
│   ├── src/main/resources/{application.yml, application-local.yml.example}
│   ├── src/test/java/
│   ├── gradle/wrapper/
│   └── build.gradle, settings.gradle, gradlew, gradlew.bat
├── frontend/                    # React + Vite. 상세: docs/convention/FRONTEND_CONVENTION.md
│   ├── src/{api,assets,components,pages,routes,store,hooks,utils,constants}/
│   ├── .env.example
│   └── package.json, package-lock.json, vite.config.js
├── docs/
│   ├── git-guide.md             # Git 협업 및 형상관리 가이드 (브랜치/커밋/PR 규칙의 원본)
│   ├── convention/              # 파트별 코드 컨벤션
│   ├── requirements/, design/   # 요구사항, 화면 설계
│   ├── erd/                     # 01_schema_ddl.sql, 02_init_data_dml.sql
│   └── api/                     # REST API 명세, error-codes.md
├── .github/                     # ISSUE_TEMPLATE/, PULL_REQUEST_TEMPLATE.md
├── docker-compose.yml          # 로컬 Oracle 확인용
├── .gitignore, .gitattributes
└── AGENTS.md                     # 이 파일
```

- Gradle Kotlin DSL 사용 시 `build.gradle.kts`, `settings.gradle.kts`를 사용함.

---

## 3. 실행 및 검증 명령

에이전트는 코드를 수정한 뒤 **반드시 해당 영역의 검증 명령을 실행하고 결과를 보고**함.

### Back-end (`backend/`에서 실행)

```bash
./gradlew classes
./gradlew test
./gradlew bootRun --args='--spring.profiles.active=local'
```

- Windows CMD에서는 `gradlew.bat`, PowerShell에서는 `.\gradlew.bat`을 사용.
- 로컬 설정이 필요하면 `application-local.yml.example`을 복사하라고 안내하되 **실제 자격 증명은 사람이 직접 입력**.
- 로컬 개발은 H2를 사용하며, `ddl-auto: validate`를 유지하면 실행 전에 H2 스키마도 준비되어 있어야 함.

### Front-end (`frontend/`에서 실행)

```bash
npm install                   # package.json 또는 package-lock.json 변경 후
npm run dev                   # 개발 서버 (포트 5173)
npm run lint                  # ESLint (경고 0 유지)
npm run build                 # 프로덕션 빌드 (PR 전 필수 통과)
```

### 로컬 Oracle (프로젝트 루트에서 실행)

```bash
docker compose up -d
docker compose ps
```

- Docker 실행 환경이 준비되어 있어야 하며, DB가 healthy 상태가 된 뒤 연결함.
- 호스트에서 접속하는 URL: `jdbc:oracle:thin:@localhost:1521/FREEPDB1`
- 계정: `SCOTT / TIGER`
- 프로젝트 테이블은 DDL을 별도로 적용해야 함.
- 기존 DB 볼륨이 있으면 환경변수 변경만으로 계정·비밀번호가 변경되지 않음.

### Git 상태 확인

```bash
git status
git log --oneline --graph -n 10
```

---

## 4. Git 작업 규칙 (에이전트 필수 준수)

### 4.1 금지 사항 및 명시된 예외

- `git push --force`, `git push -f`, `git push --force-with-lease`는 모든 브랜치에서 금지.
- 이미 푸시된 커밋에 대한 `git rebase`, `git commit --amend` 금지. 본인 피처 브랜치도 예외 없음.
- `main`, `dev` 브랜치에서 직접 커밋 또는 푸시 금지.
- `git reset --hard` — 사용자가 명시적으로 요청하고 삭제될 변경 내용을 확인한 경우를 제외하고 금지.
- `git revert`는 모든 브랜치에서 예외 없이 금지.
- 원격에 푸시된 커밋을 되돌리기 위한 `git reset` 사용 금지.
- 이미 공유된 변경을 취소해야 하면 작업 브랜치에서 필요한 코드를 수정하고, 새 커밋과 PR로 반영.
- `git rm -r --cached .`를 단독 실행하지 않음 — `.gitignore` 재적용 목적일 때만 `git add .` → `git commit`까지 한 번에 수행.
- 사용자 확인 없는 `git push` 금지. 커밋까지만 수행하고 푸시 여부는 사용자에게 묻는 것이 기본.

### 4.2 브랜치

- 새 작업 시작 전: `git switch dev && git pull origin dev` 후 새 브랜치 분기.
- 기존 작업 브랜치에서 같은 작업을 이어가는 경우에는 해당 브랜치를 사용.
- 명명 규칙:
  - 기능: `feature/{be|fe}/{이슈번호}-{기능명}` → `feature/be/12-jwt-auth`, `feature/fe/15-login-page`
  - 버그: `fix/{be|fe}/{이슈번호}-{결함명}` → `fix/be/33-token-expired-bug`
  - 긴급: `hotfix/{이슈번호}-{패치명}` (main에서 분기, main과 dev 양쪽 병합)
- 이슈 번호를 모르면 **임의로 만들지 말고 사용자에게 확인**.
- 현재 브랜치가 `main`/`dev`인 상태에서 코드 수정 요청을 받으면, 수정 전에 피처 브랜치 생성을 먼저 제안.

### 4.3 커밋 메시지 (Conventional Commits)

```text
<타입>: <제목>                  ← 간결하게 작성, 마침표 없음, 한글 허용

<본문 (선택)>                   ← 변경 이유와 요점

Resolves: #<이슈번호>           ← 이슈 완료 시
```

- 타입: `init`, `feat`, `fix`, `refactor`, `style`, `docs`, `test`, `chore`
- 예시:
  - `feat: Member 도메인 엔티티 클래스 구현`
  - `fix: 주문 수량 음수 입력 시 유효성 검증 오류 수정`
  - `chore: axios 의존성 패키지 업데이트`
- 하나의 커밋은 하나의 논리 단위. 서로 다른 기능을 한 커밋에 묶지 않음.
- 커밋 메시지에 AI 도구 서명이나 `Generated by` 류 문구를 넣지 않음.

### 4.4 스테이징 전 점검

`git add` 전에 `git status`를 확인하고 아래 파일이 목록에 있으면 **Stage하지 않음**:

- Back-end:
  - `.project`, `.classpath`, `.settings/`, `bin/`, `target/`
  - `.gradle/`, `build/`, `out/`, `*.iml`
  - `backend/src/main/resources/application-local.yml`, `application-secret.yml`
- Front-end:
  - `node_modules/`, `dist/`, `.vite/`
  - `.env.local`, `.env.*.local`
- 기타:
  - `*.log`, `*.hprof`
  - 100MB 이상 파일 (`*.dmp`, 영상, 압축)

- `.idea/`는 개인 설정을 제외하고 팀에서 합의한 공통 설정만 추적함.
- Gradle Wrapper(`gradlew`, `gradlew.bat`, `gradle/wrapper/`)는 Git으로 관리함.
- 제외 대상 파일이 추적 중이면 `.gitignore` 상태를 보고하고 사용자 판단을 요청.

### 4.5 PR

- PR 생성 시 base는 항상 `dev` (hotfix 제외).
- 본문은 `.github/PULL_REQUEST_TEMPLATE.md` 양식(변경 요약 / 관련 이슈 / 세부 변경 사항 / 테스트 및 검증 결과 / 리뷰어 요청 사항)을 채움.
- 테스트 및 검증 결과에는 **실제로 실행한 명령과 결과만** 기재. 실행하지 않은 검증을 완료로 표기하지 않음.
- PR 병합은 사람이 Approve 후 Squash and merge로 수행. 에이전트는 병합하지 않음.

---

## 5. 코드 작성 규칙 (요약 — 상세는 컨벤션 문서)

### Back-end

- 도메인별 패키지 내부에 Controller, Service, Repository, Entity, DTO 배치.
- 요청 처리 방향: `Controller → Service → Repository`. Controller에서 Repository 직접 호출 금지.
- 엔티티를 요청/응답에 직접 노출하지 않음. DTO(`{도메인}{용도}Request/Response`) 사용.
- JSON 응답은 `ApiResponse<T>` 공통 포맷. 204 응답에는 본문을 반환하지 않음.
- 업무 예외는 `BusinessException + ErrorCode`로 표현.
- 생성자 주입만 사용. `System.out.println` 금지, SLF4J 로깅.
- JPA 엔티티: `protected` 기본 생성자, `FetchType.LAZY`, Setter 전체 공개 금지.
- `ddl-auto`는 `validate` 고정.
- **실제 스키마 변경 시 `docs/erd/01_schema_ddl.sql`을 같은 브랜치에서 함께 수정**하고 기존 DB에 적용할 SQL을 PR에 기록.
- 엔티티 메서드·주석처럼 스키마에 영향이 없는 변경은 DDL 수정 대상이 아님.
- Oracle 전용 기능은 H2 테스트만으로 검증 완료 처리하지 않음.
- 새 설정 키 추가 시 `application-local.yml.example`에도 반영.

### Front-end

- 함수형 컴포넌트 + Hooks만 사용. 컴포넌트 파일의 대표 컴포넌트 하나를 default export.
- 업무 API 호출은 `컴포넌트 → Zustand store 액션 → api 모듈 → axiosInstance` 경유.
- Axios 직접 import와 인스턴스 생성은 `api/axiosInstance.js`에서 담당.
- 라우트 경로는 `constants/routes.js` 상수 사용, 하드코딩 금지.
- 전역 상태는 공유가 필요한 것만 Zustand로 관리. 폼 입력 등은 `useState`.
- selector로 필요한 상태와 액션만 구독.
- 상태는 `set`으로 갱신하고 객체·배열을 직접 수정하지 않음.
- 클라이언트용 사용자 정의 환경변수는 `VITE_` 접두사 사용. 새 키는 `.env.example`에 반영. 비밀 키 금지.
- `console.log`, 미사용 import 제거. `useEffect` 의존성 경고 무시 금지.

### 공통

- 인코딩 UTF-8, 줄바꿈은 저장소의 `.gitattributes` 기준.
- `gradlew`, `*.sh`는 LF. `gradlew.bat`은 CRLF 허용.
- 들여쓰기: Java 공백 4칸, JS/JSX 공백 2칸.
- 주석 처리된 폐기 코드는 삭제. 주석은 이유를 설명.
- 팀 공통 파일은 **파트장 리뷰 없이 수정하지 않음**.
- 외부 라이브러리 추가는 사용자(파트장)에게 먼저 제안하고 승인 후 적용.
- 의존성 변경 시 관련 파일을 함께 커밋:
  - Back-end: `build.gradle` 또는 `build.gradle.kts`
  - Front-end: `package.json`, `package-lock.json`

---

## 6. 보안 및 민감 정보

- 실제 API 키, DB 비밀번호, JWT 시크릿, 접근 토큰 등 자격 증명은 **사람이 직접 입력하고 직접 삭제**함.
- 에이전트는 Git 추적 여부와 관계없이 실제 자격 증명을 파일에 입력·수정·삭제하지 않음.
- 실제 자격 증명을 코드, 커밋 메시지, 문서에도 기록하지 않음.
- 추가 자격 증명이 필요하면 설정 키와 입력 위치만 안내하고 사용자의 수동 입력을 기다림.
- 사용자가 자격 증명을 채팅에 붙여넣더라도 에이전트가 대신 파일에 입력하지 않음.
- 기존 자격 증명의 수정·삭제가 필요해도 직접 처리하지 않고 사용자에게 안내.
- 예시 값은 `CHANGE_ME` 등 명백한 더미만 사용.
- 단, 팀에서 합의한 로컬 개발용 Oracle 계정 `SCOTT / TIGER`와 Docker 관리자용 더미 비밀번호는 로컬 설정·문서 예시에 사용 가능.
- 위 예외는 운영 자격 증명에는 적용하지 않음.
- diff에 `password`, `secret`, `token` 값이 포함되면 실제 자격 증명인지 확인. 실제 값이면 커밋을 중단하고 사람이 직접 수정·삭제하도록 안내.

---

## 7. 작업 흐름 (에이전트 표준 절차)

1. **파악**: 관련 이슈 번호, 파트(BE/FE), 영향 범위 확인. 불명확하면 질문.
2. **동기화**: 새 작업은 `git switch dev && git pull origin dev`. 기존 작업을 이어가면 해당 작업 브랜치 사용.
3. **분기**: 새 작업은 명명 규칙에 맞는 피처 브랜치 생성.
4. **구현**: 컨벤션 문서 준수. 실제 스키마 변경이면 DDL 동반 수정. 설정 키 추가면 example 파일 동반 수정.
5. **검증**: BE `./gradlew test`, FE `npm run lint`, `npm run build` 실행. 실패 시 수정 후 재실행.
6. **스테이징 점검**: `git status`로 금지 파일 미포함 확인.
7. **커밋**: Conventional Commits 형식. 논리 단위별 분리.
8. **보고**: 변경 파일 목록, 실행한 검증 명령과 결과, 푸시/PR 진행 여부를 사용자에게 질문.

---

## 8. 자주 발생하는 상황별 대응

| 상황 | 대응 |
|---|---|
| push 시 non-fast-forward 거부 | `git pull origin <브랜치>` 후 충돌 해결 → 재푸시. pull은 rebase 대신 merge 방식 사용. 강제 푸시 금지. |
| 충돌 마커 발생 | 양측 변경 의도를 사용자에게 요약 보고하고 합의된 결과로 편집. 임의로 한쪽 폐기 금지. |
| main/dev에서 실수로 커밋됨 | 별도 작업 브랜치로 커밋을 보존 → 사용자 확인 후 원래 브랜치를 해당 원격 브랜치 상태로 복구 → 작업 브랜치로 전환. |
| .gitignore 추가 후에도 파일이 추적됨 | `.gitignore` 재적용 목적으로 `git rm -r --cached .` → `git add .` → `git commit` 연속 실행. |
| pull 후 import 에러 | BE: IntelliJ에서 Gradle 프로젝트 동기화. FE: `npm install` 실행. |
| 로컬 브랜치가 master로 생성됨 | `git branch -M main`. |
| 100MB 초과 파일로 push 거부 | `git reset --soft HEAD~1` → 파일 unstage → `.gitignore` 등록 → 재커밋. |
| 줄바꿈 경고 발생 | `.gitattributes`와 실제 diff를 확인하고 저장소의 줄바꿈 정책에 맞춰 처리. |

---

## 9. 하지 말아야 할 것 (요약)

- 금지된 Git 명령 실행 (4.1절)
- 확인 없이 푸시, PR 병합, 브랜치 삭제
- 이미 푸시된 커밋 rebase·amend 또는 강제 푸시
- 모든 브랜치에서 `git revert` 실행
- 실제 자격 증명을 대신 입력·수정·삭제하거나 문서·커밋 메시지에 기록
- 실제 스키마 변경 시 DDL 누락
- 실행하지 않은 테스트를 통과했다고 보고
- 이슈 번호·브랜치명을 임의로 지어내기
- 팀 공통 파일(`common/`, `axiosInstance.js`, `useAuthStore.js`, `AppRouter.jsx`, ESLint/Prettier 설정, `.gitignore`, `.gitattributes`)을 파트장 리뷰 없이 수정
- 외부 라이브러리를 승인 없이 추가