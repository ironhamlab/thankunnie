## 📌 변경 요약 (Summary)
<!-- 이 PR에서 무엇을, 왜 변경했는지 한두 문장으로 작성합니다. -->
- 

## 🔗 관련 이슈 (Related Issue)
<!-- 병합 시 이슈가 자동으로 닫히도록 키워드를 사용합니다. 예: Resolves #12 -->
- Resolves #

## 🏷️ 작업 파트 및 유형 (Part & Type)
- 파트: [ ] Back-end  [ ] Front-end  [ ] Database  [ ] 공통/문서
- 유형: [ ] feat  [ ] fix  [ ] refactor  [ ] style  [ ] docs  [ ] test  [ ] chore

## 🛠️ 세부 변경 사항 (Changes)
<!-- 변경된 클래스·컴포넌트·파일 단위로 개조식 작성 -->
<!-- DB 스키마 변경 시 [DB] 태그로 변경 테이블/컬럼을 반드시 명시 -->
- 
- 
- [DB] (해당 시) 

## ✅ 테스트 및 검증 결과 (Test & Verification)
<!-- 실제로 실행한 명령과 결과만 기재합니다. -->
- [ ] BE: `./mvnw test` 전체 통과
- [ ] BE: 로컬 기동 후 Swagger / Postman으로 API 응답 확인
- [ ] FE: `npm run lint` 경고 0, `npm run build` 성공
- [ ] FE: 브라우저에서 실제 화면 동작 확인
- 검증 내용:
  - 

## 📸 스크린샷 / API 응답 예시 (Optional)
<!-- 화면 변경은 Before/After 캡처, API는 요청·응답 예시 -->

## 🔍 셀프 체크리스트 (Self Check)
- [ ] base 브랜치가 `develop`으로 설정되어 있다 (hotfix 제외)
- [ ] 브랜치명과 커밋 메시지가 컨벤션(`feature/{be|fe}/{이슈번호}-{기능명}`, Conventional Commits)을 따른다
- [ ] `application-local.yml`, `.env.local`, 토큰·비밀번호 등 민감 정보가 포함되지 않았다
- [ ] IDE 메타파일(`.project`, `.classpath`, `.settings/`, `target/`, `node_modules/`, `dist/`)이 포함되지 않았다
- [ ] 엔티티/매퍼 컬럼 변경 시 `docs/erd/01_schema_ddl.sql`을 함께 수정했다
- [ ] 새 설정 키·환경변수를 `application-local.yml.example` / `.env.example`에 반영했다
- [ ] `System.out.println`, `console.log`, 주석 처리된 폐기 코드를 제거했다
- [ ] 최신 `develop`을 pull 받아 충돌이 없음을 확인했다

## 💬 리뷰어 요청 사항 (Notes for Reviewer)
<!-- 중점적으로 봐주었으면 하는 부분, 고민했던 설계 포인트, 미완료 사항 등 -->
- 
