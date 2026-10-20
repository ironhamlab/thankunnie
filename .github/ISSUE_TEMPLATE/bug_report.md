---
name: "🐛 버그 리포트 (Bug Report)"
about: "발견한 결함 또는 오류를 보고합니다."
title: "[BUG][BE|FE] 버그 현상을 간결하게 작성"
labels: ["bug"]
assignees: []
---

## 1. 버그 설명 (Description)
<!-- 어떤 문제가 발생했는지 한두 문장으로 기술합니다. -->
- 

## 2. 발생 파트 (Part)
- [ ] Back-end (Spring Boot)
- [ ] Front-end (React)
- [ ] Database
- [ ] 공통 / 환경설정

## 3. 재현 절차 (Steps to Reproduce)
1. 
2. 
3. 

## 4. 기대 결과 vs 실제 결과 (Expected vs Actual)
| 기대 결과 (Expected) | 실제 결과 (Actual) |
|---|---|
|  |  |

## 5. 에러 로그 / 스크린샷 (Logs & Screenshots)
<!-- 콘솔 로그, 스택 트레이스, 네트워크 응답, 화면 캡처 등을 첨부합니다. -->
<!-- ⚠️ 토큰·비밀번호·DB 계정 정보는 반드시 가리고 첨부하세요. -->
```
여기에 로그를 붙여넣습니다.
```

## 6. 발생 환경 (Environment)
- OS: (예: Windows 11 / macOS 15)
- 브랜치 / 커밋: (예: `develop` / `a1b2c3d`)
- 브라우저: (FE인 경우, 예: Chrome 141)
- 기타: (Java 21, Node 22, Oracle 21c XE 등)

## 7. 추정 원인 및 해결 방안 (Optional)
<!-- 원인을 짐작하고 있다면 작성합니다. -->
- 

---
> **브랜치 명명 안내**: `fix/{be|fe}/{이슈번호}-{결함명}` 브랜치에서 수정하며, 운영(main) 긴급 장애는 `hotfix/{이슈번호}-{패치명}`을 사용합니다.
