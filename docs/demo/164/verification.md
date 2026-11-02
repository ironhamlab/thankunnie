# UNNIE-164 검증 및 적용 안내

## 변경 범위

- `docs/erd/03_demo_data_dml.sql`: 기존 데이터 유지, 신규 서울 매장·시술·영업시간·모집 시간대 추가.
- `docs/demo/164/demo164-additions.sql`: 기존 DB용 추가 전용 SQL. 전체 03 또는 cleanup 재실행을 피한다.
- `docs/demo/164/images/`: 실제 생성한 JPEG 90장. 원본 생성 도구는 built-in imagegen이다.
- `image-plan.json`: 90개 프롬프트, `data-plan.json`: 50개 매장과 150개 시술 연결 계획.
- `image-manifest.json`: 슬롯별 파일·SHA-256·서로 다른 UUID 키 200개.
- `image-manifest.json.s3-verification.json`: 실제 S3 전체 대조 기록. `api-verification.json`: 임시 앱의 실제 이미지 API 검증 기록.
- `location-sources/`: 서울 25개 구의 공개 주소·좌표 대조 자료. 실제 업체명·연락처는 수집하지 않았다.
- `allocation.md`: 구별·매장별 일반/모델 시술 배분.
- PowerShell/Node 스크립트: 이미지 압축·검수·manifest·S3 업로드·SQL 생성·H2 검증 재현용.
- `frontend/src/utils/exploreVacancy.js`: 로컬에서 이미지 상대 경로를 API origin 기준으로 해석.

스키마·공통 Axios·인증·라우터·환경 설정·의존성 파일은 변경하지 않았다. 실제 자격 증명은 입력·수정·삭제하지 않았다.
작업 브랜치는 `chore/164-demo-data`다. 사용자 기존 미추적 파일 3개는 보존했다.

## 수량

| 항목 | 신규 추가 |
|---|---:|
| 서울 매장 | 50 |
| 일반 시술 | 130 |
| 모델 시술 | 20 |
| 모델 모집을 하는 매장 | 16 |
| 요일별 영업시간 | 350 |
| OPEN 모집 시간대 | 814 |

모든 신규 매장에 일반 시술 2~4개가 있다. 일반 시술 2개인 매장 30곳, 3개인 매장 10곳, 4개인 매장 10곳이다.
모델 모집은 12개 매장에 1개씩, 4개 매장에 2개씩만 있다.
업종별로 매장 10개·일반 시술 26개·모델 시술 4개다.
강남 8, 마포 5, 서초·송파 각 4, 성동 3, 용산·영등포·종로·중구·광진·강동 각 2, 나머지 14개 구 각 1개다.
상세 표는 [allocation.md](allocation.md)에 있다.

전체 01·02·03을 적용하는 새 DB 기준으로 기존 매장 13개와 신규 50개를 합쳐 63개가 된다.
기존 예약·결제 시연 매장과 데이터를 보존하는 구성이다. 기존 DB에는 데이터 상태에 따라 전체 합계가 다를 수 있다.

## 이미지 및 S3

- 매장 50장: 공간·분위기 중심으로 매장마다 다른 파일.
- 시술 40장: 네일 10, 헤어 12, 속눈썹 6, 피부 관리 6, 메이크업 6.
- 같은 구체적인 시술 안에서 최대 5회 재사용한다. 슬롯마다 별도 S3 객체를 사용한다.
- 로컬 실제 JPEG 90개, 파일 SHA-256도 90종, 누락 0개.
- 전체 14,253,890 bytes, 최대 파일 246,414 bytes. 매장 긴 변 1280px, 시술 긴 변 1024px 이하.
- S3 대상: `s3://thankunnie-images-dev/shop-images/`, 리전 `ap-southeast-2`.
- 매장 50개 + 시술 150개 슬롯을 위한 객체 총 200개를 저장·조회했다.
- 공개 ACL·버킷 설정·기존 객체는 변경하지 않았다. 동일 해시의 기존 객체는 건너뛰고, 다른 객체의 덮어쓰기는 금지한다.
- 첫 객체는 다시 다운로드하여 원본과 실제 SHA-256 일치를 확인했다. 전체 업로드는 로컬 SHA-256과 원격 메타데이터를 대조한다.
- DB에는 S3 공개 URL 대신 기존 백엔드 이미지 API 상대 경로를 사용한다.

검수 시트: [매장 50장](shop-preview.jpg), [시술 40장](treatment-preview.jpg).

## 실제 실행한 검증

| 명령·작업 | 결과 |
|---|---|
| `frontend`의 `npm run lint` | 통과, 경고 0 |
| `frontend`의 `npm run build` | 통과, 390개 모듈 변환 |
| `backend`의 `gradlew.bat classes --no-daemon` | 통과 |
| `backend`의 `gradlew.bat test --no-daemon` | 통과, 97개 클래스·825개 테스트, 실패/오류/건너뜀 0 |
| `verify-h2.ps1` | 통과. 실제 01·02·03 실행, 수량·관계·가격·시간·모델 조건·25개 구 확인 |
| `verify-h2.ps1 -RequireImages` | 통과. S3 검증 완료한 이미지 경로 200개 포함해 전체 SQL·재실행 검사 |
| 추가 SQL 재실행 + 재집계 | 통과. 매장·시술·영업시간 중복 없음 |
| 시드 이후 시퀀스 기반 신규 매장·시술 삽입 | PK 충돌 없이 성공 |
| PowerShell 구문 분석, Node `--check` | 통과 |
| 실제 JPEG 90개와 manifest 200개 슬롯 검사 | 누락 0, UUID 키 200종, 시술 사진 재사용 최대 5회 |
| 실제 S3 저장·HEAD 조회, 첫 파일 다운로드 | 성공 |
| `verify-image-api.ps1` | 통과. 매장 상세 50건 및 이미지 API 200건 정상 응답, 모든 실제 JPEG 해시 일치 |
| `git diff --check` | 공백 오류 없음 |

처음 Java/Gradle 실행은 환경 오류가 있었다. 설치된 Java 21 실행 파일과 이번 프로세스의 짧은 임시 소켓 경로를 사용해 해결했다.
저장소 설정은 바꾸지 않았다. backend 검증 명령 실행 시 사용한 추가 환경은 다음과 같다.

```powershell
$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=C:/UCAMP/Final/thankunnie/backend/build/socket-temp'
```

AWS CLI의 기본 인증서 검증 오류는 Windows 신뢰 저장소의 공개 CA bundle로 해결했다. TLS 검증은 끄지 않았다.
AWS 프로필은 기존 `thankunnie`를 사용했다. 버킷 목록·리전 조회 권한은 없어 사용자가 알려준 대상과 리전을 사용했다.
퍼블릭 액세스 차단 설정 조회도 권한 부족으로 확인하지 못했다. 설정을 변경하지 않았으며 AWS 콘솔에서 사람이 확인해야 한다.

이미지 API 검증 앱은 18084 포트, `test` 프로필, `jdbc:h2:mem:demo164_http` 전용 메모리 DB로 실행했다.
`APP_SCHEDULING_ENABLED=false`로 예약·문자 등 주기 작업을 비활성화했다. 기존 8080 앱이나 실제 DB는 건드리지 않았다.
S3는 확인받은 실제 시연 버킷을 읽었다. TLS는 Windows 신뢰 저장소로 검증했다.
해당 임시 JVM에는 다음 옵션을 함께 사용했으며 저장소나 시스템 설정은 바꾸지 않았다.

```text
-Djdk.net.unixdomain.tmpdir=C:/UCAMP/Final/thankunnie/backend/build/socket-temp
-Djavax.net.ssl.trustStoreType=Windows-ROOT
-Djavax.net.ssl.trustStore=NONE
```

임시 앱은 이미지 API 검증 후 종료했다. 실행한 GET 요청은 앱 데이터나 S3 객체를 수정하지 않는다.

## 적용 방법과 직접 확인할 부분

1. 로컬 앱 실행 설정을 확인한다.

   ```text
   AWS_PROFILE=thankunnie
   SHOP_IMAGE_STORAGE=s3
   SHOP_IMAGE_S3_BUCKET=thankunnie-images-dev
   AWS_REGION=ap-southeast-2
   SHOP_IMAGE_S3_PREFIX=shop-images/
   ```

2. local H2는 앱 시작 시 전체 03을 자동 로드한다. 새 프로세스 시작 전에 현재 로컬 DB가 H2인지 확인한다.
   AWS 콘솔에서는 대상 버킷의 퍼블릭 액세스 차단이 켜져 있는지도 확인한다.
3. 새 Oracle 전용 시연 스키마라면 01 → 02 → 03 순서로 실행한다. 기존 테이블이 있는 DB에 01을 실행하지 않는다.
4. 이미 초기·시연 데이터가 있는 Oracle에는 대상 서버·서비스명·스키마를 먼저 확인한 뒤 `demo164-additions.sql`만 실행한다.
   자동 커밋을 끄고 `verify-data.sql`을 실행한 후 사람이 COMMIT한다. 기존 cleanup은 실행하지 않는다.
5. 목록·매장 상세·일반 시술·모델 모집에서 이미지 크롭과 시술 결과가 자연스러운지 확인한다.
6. 지도에서 가상 매장 안내와 주소 지점 표시를 확인한다. 실제 입점 정보나 측량 좌표로 해석하지 않는다.
7. 신규 50개 매장은 비밀번호 해시가 더미라 로그인할 수 없다. 기존 사장님·결제 시연 계정은 유지한다.
8. 배포할 때도 같은 버킷·리전·prefix와 S3 접근 권한을 설정한다. DB 이미지 URL은 백엔드 상대 경로이므로 그대로 사용할 수 있다.

## 남은 환경 검증

- 기존 로컬 DB나 운영/배포 Oracle에는 적용하지 않았다. 적용 대상 환경 확인이 필요하다.
- Oracle 21c XE 실제 실행은 아직 하지 않았다. Oracle 대상 서버·서비스명·스키마가 확정되지 않았다.
  H2 통과를 Oracle 검증 완료로 취급하지 않는다.
- 알림 지역 선택은 현재 일부 지역만 제공하고 홈 검색에는 구별 선택 UI가 없다. 나머지 구는 5자리 REGION_CODE로 API 조회할 수 있다.
- 푸시·PR 생성·병합은 수행하지 않는다.
