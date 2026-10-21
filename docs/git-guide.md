# Git 협업 컨벤션

> 적용 범위: 저장소 전체
> 코드 작성 규칙은 Back-end·Front-end 컨벤션을 따른다.

## 1. 브랜치

- `main`: 발표·배포 기준 브랜치
- `develop`: 개발 통합 브랜치
- `main`, `develop`에 직접 커밋·푸시하지 않고 PR로 반영한다.
- 일반 작업은 `develop`에서 분기하여 `develop`으로 PR을 생성한다.
- `develop → main`은 팀에서 합의한 발표·배포 시점에 반영한다.
- `main`에 반영한 변경은 `develop`에도 반영한다.

| 종류 | 이름 |
|---|---|
| 기능 | `feature/{be|fe}/{이슈번호}-{작업명}` |
| 버그 수정 | `fix/{be|fe}/{이슈번호}-{작업명}` |
| 구조·성능 개선 | `refactor/{be|fe}/{이슈번호}-{작업명}` |
| 문서 | `docs/{이슈번호}-{작업명}` |
| 서버·Docker·배포 설정 | `infra/{이슈번호}-{작업명}` |
| 기타 유지보수 | `chore/{이슈번호}-{작업명}` |
| 배포 코드 긴급 수정 | `hotfix/{이슈번호}-{작업명}` |

- 브랜치명은 영어 소문자 kebab-case로 작성한다.
- 실제 이슈 번호를 사용한다.
- hotfix는 `main`에서 분기하고, 수정 결과를 `main`과 `develop` 양쪽에 반영한다.

```text
feature/be/12-login-api
feature/fe/15-login-page
infra/41-oracle-compose
```

## 2. 커밋

```text
<type>(<scope>): <subject>

<body: optional>
```

- type, scope, subject는 영어로 작성한다.
- subject는 명령형으로 작성하고 끝에 마침표를 붙이지 않는다.
- 부연 설명인 body는 한글로 작성해도 된다.
- 하나의 커밋에는 하나의 논리적 변경을 담는다.

| 타입 | 용도 |
|---|---|
| `init` | 프로젝트·모듈 최초 구성 |
| `feat` | 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 기능 변경 없는 구조·성능 개선 |
| `style` | 코드 포맷 변경 |
| `docs` | 문서 수정 |
| `test` | 테스트 추가·수정 |
| `chore` | 의존성·빌드·설정·CI/CD 등 유지보수 |

- 화면 스타일링은 `feat` 또는 `fix`를 사용한다.
- scope는 `auth`, `order`, `store`, `docker` 등 변경 영역을 적는다.
- merge·revert 명령의 자동 생성 메시지는 예외로 허용한다.
- AI 도구 서명은 넣지 않는다.

```text
feat(auth): add login API

이메일과 비밀번호로 로그인하는 API를 추가한다.
```

## 3. 이력 관리

- 이미 푸시한 커밋은 rebase하거나 amend하지 않는다.
- 다른 브랜치의 변경은 merge로 반영한다.
- 모든 강제 푸시를 금지한다. `--force-with-lease`도 사용하지 않는다.
- 이미 공유된 커밋을 되돌릴 때는 `git revert`를 사용한다.
- 충돌 해결 시 다른 사람의 변경 의도가 불명확하면 해당 작업자에게 확인한다.

## 4. PR 및 리뷰

- PR에 변경 요약, 관련 이슈, 실제 검증 결과를 작성한다.
- 작성자 외 팀원 최소 1명의 승인 후 병합한다. 파트장 승인은 필수가 아니다.
- 실패한 검증과 미해결 수정 요청은 병합 전에 해결한다.
- 작업 브랜치의 PR은 Squash and merge로 병합한다.
- `develop → main`, `main → develop`은 merge commit으로 병합한다.
- 다른 작업에 영향을 주는 공통 코드·API·DB 변경은 관련 작업자와 사전에 공유한다.

## 5. 검증 및 커밋 제외 대상

- Back-end 코드 변경: `./gradlew test` 통과
  - Windows: `gradlew.bat test`
- Front-end 코드 변경: `npm run lint`, `npm run build` 통과
- 화면 변경: 브라우저에서 실제 동작 확인
- 실행하지 않은 검증을 통과했다고 기록하지 않는다.

다음 항목은 커밋하지 않는다.

- 실제 비밀번호·API 키·토큰 등 비밀정보
- 개인 환경 설정: `application-local.yml`, `.env.local` 등
- 캐시·빌드 결과: `.gradle/`, `build/`, `node_modules/`, `dist/` 등
- 개인 IDE 설정, 로그·덤프 파일

Gradle Wrapper, `package-lock.json`, 환경 설정 example 파일은 커밋한다.
합의된 로컬 개발용 `SCOTT / TIGER`와 명백한 더미 값은 예외로 허용한다.

## 6. 관련 변경 반영

- 실제 DB 스키마 변경은 기준 DDL과 기존 DB 적용 SQL을 함께 반영한다.
- 환경 설정 키 추가는 관련 example 파일에도 반영한다.
- 의존성 변경은 빌드 설정과 lock 파일을 함께 반영한다.