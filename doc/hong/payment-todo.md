# 결제(포트원 V1 + KG이니시스) 이어서 할 일

> 브랜치: `feature/be/14-payments` / 마지막 작업: 2026-10-22
> 이 파일은 개인 메모용이라 커밋하지 않음 (`doc/` 폴더 전체 미추적)

## 어디까지 했나

- 포트원 콘솔: KG이니시스 테스트 채널(V1, 결제창 일반결제) 추가 완료
- 코드 작성 완료: `backend/src/main/java/com/ucamp/app/domain/payment/` (client, controller, service, dto/request, dto/response)
- `ErrorCode`에 `PAYMENT_001`, `PAYMENT_002` 추가 + `docs/api/error-codes.md` 반영
- `application.yml`에 portone 설정 추가, `application-local.yml`에 키 입력 완료
- `static/payment-test.html`: 식별코드 입력, 100원 결제
- 결제창에서 결제는 성공 → `/api/v1/payments/verify`에서 `COMMON_500` 발생

## 500 에러 원인

`PKIX path building failed` = Java가 포트원 인증서를 거부함.
학원 네트워크(LG CNS 보안팀 장비)가 HTTPS를 중간에서 검사하며 인증서를 바꿔치기해서 생김.
→ 코드 문제 아님. 집 등 다른 네트워크에서는 안 날 수도 있음.

## 내일 할 순서

1. **VM 옵션 추가** (LG CNS 네트워크에서만 필요, 넣어 둬도 문제없음)
   - IntelliJ: `Application` ▼ → Edit Configurations → Modify options → Add VM options
     ```text
     -Djavax.net.ssl.trustStoreType=Windows-ROOT
     ```
   - 터미널로 실행할 때는 `bootRun` 전에:
     ```powershell
     $env:JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStoreType=Windows-ROOT"
     ```
   - ⚠️ "SSL 검증 끄기" 코드는 절대 넣지 않기
2. **서버 실행** → `Application.java` 초록 ▶ → `Started Application` 확인
3. **테스트**: `http://localhost:8080/payment-test.html` → 100원 결제
   - 성공 기준: `"success": true`, `"status": "paid"`, `"amount": 100`
   - Swagger(`/swagger-ui.html`)에서 같은 impUid로 `/api/v1/payments/verify` 호출
   - 없는 값(`imp_000`)으로 `PAYMENT_001` 나오는지 확인
4. 그래도 같은 에러면 → JDK cacerts에 LG CNS 인증서 직접 추가하는 방법 물어보기

## 커밋 전에 고칠 것

- [ ] `application.yml` CORS 줄 끝 쉼표 삭제: `...localhost:5173},` → `...localhost:5173}`
- [ ] `application-local.yml.example`에 portone `CHANGE_ME` 항목 추가
- [ ] 결제 파일 8개 + `application.yml` 파일 끝 줄바꿈 추가
- [ ] import 순서 팀 스타일로: `java` → `jakarta` → `org` → 기타 → `com.ucamp` (그룹 사이 빈 줄)
- [ ] `ErrorCode.java`의 `// PAYMENT` 위에 빈 줄, `error-codes.md` 겹친 빈 줄 하나 삭제
- [ ] (개선) `PortOneClient`에서 `ResourceAccessException` → `PAYMENT_002`로 변환
- [ ] `.\gradlew.bat test` 실행
- [ ] stage 금지: `application-local.yml`, `static/payment-test.html`, `doc/`
- [ ] PR에 "LG CNS 네트워크에서는 VM 옵션 필요" 한 줄 적기, 리뷰어에 백엔드 파트장 (ErrorCode 공통 파일 수정)

## 그다음 단계

PAYMENT 엔티티 + DDL → prepare API(서버에서 주문번호·금액 결정) → verify에서 금액 비교 후 저장
