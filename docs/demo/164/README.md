# UNNIE-164 시연 이미지 준비

이미지는 비공개 S3 객체로 저장하고 기존 백엔드 이미지 API가 중계한다.
DB에는 버킷 공개 URL이나 만료되는 presigned URL을 저장하지 않는다.
스키마·자격 증명 파일은 변경하지 않는다. 탐색 카드의 상대 이미지 URL은 로컬 API origin 기준으로 보정한다.

확인받은 S3 대상은 `thankunnie-images-dev`, 리전 `ap-southeast-2`(시드니), prefix `shop-images/`다.
기존 `thankunnie` 프로필로 이미지 1장 저장·조회 및 내려받은 실제 파일의 SHA-256 일치를 확인했다.
버킷 설정·ACL·IAM 정책은 변경하지 않았다.

로컬 실행 설정에서 다음 값을 사람이 확인한다. 실제 비밀 키는 기존 AWS 프로필을 사용한다.

```text
AWS_PROFILE=thankunnie
SHOP_IMAGE_STORAGE=s3
SHOP_IMAGE_S3_BUCKET=thankunnie-images-dev
AWS_REGION=ap-southeast-2
SHOP_IMAGE_S3_PREFIX=shop-images/
```

## 이미지 계획

- 매장 이미지 50장: 서로 다른 매장의 공간을 각각 생성한다.
- 시술 이미지 40장: 네일 10, 헤어 12, 속눈썹 6, 피부 관리 6, 메이크업 6.
- 시술 슬롯 150개에 같은 구체적인 시술 종류의 이미지로만 최대 5회 재사용한다.
- S3 객체는 재사용 슬롯마다 다른 UUID 파일명을 쓴다. 한 시술 이미지 교체 시 다른 시술의 객체가 삭제되지 않게 한다.
- 따라서 생성 원본은 90장이지만 신규 50개 매장·150개 시술 연결에 필요한 S3 객체는 200개다.
- `image-plan.json`: 생성 프롬프트. `data-plan.json`: 제안한 추가 매장·시술 배분.
- `images/`: 실제로 생성하고 JPEG로 압축한 파일만 저장한다. 파일이 없는 슬롯은 생성 완료로 취급하지 않는다.
- 최대 긴 변은 매장 1280px, 시술 1024px이며 원본 비율을 보존한다. JPEG 품질 85.
- 로고·실제 업체명·식별 가능한 전체 얼굴을 생성하지 않는다. 시술 이미지는 결과 중심의 확대 사진이다.

## 직접 확인할 항목

1. 기존 시연 매장 11개와 초기 매장 2개를 유지하고 신규 서울 매장 50개를 추가할지 확인한다.
2. 배포 앱에서 쓰는 `SHOP_IMAGE_S3_BUCKET`, `AWS_REGION`, `SHOP_IMAGE_S3_PREFIX`를 확인한다.
   앱 기본 리전은 `ap-northeast-2`, 기본 prefix는 `shop-images/`다. 로컬 AWS 프로필의 리전과 같다고 가정하지 않는다.
3. 버킷 비공개 및 Public Access Block을 유지한다. 서버 IAM에 해당 prefix의 PutObject/GetObject/DeleteObject 권한이 있어야 한다.
   업로드 PC에는 PutObject/GetObject가 필요하다. SSE-KMS를 사용하는 경우 필요한 KMS 권한도 확인한다.
   현재 프로필에는 Public Access Block 조회 권한이 없어 이 설정은 AWS 콘솔에서 직접 확인한다.
4. 실제 AWS 인증 정보는 사람이 AWS CLI/서버 역할 등 기존 방식으로 설정한다. 채팅이나 저장소에 키를 넣지 않는다.
5. 이 PC의 기본 AWS CLI는 인증서 검증 오류를 반환했다. Windows 신뢰 저장소의 공개 CA bundle을 사용하면 연결된다.
   조직의 신뢰할 수 있는 CA bundle이 필요한 환경이면 `-CaBundle` 또는 `AWS_CA_BUNDLE`로 지정한다.
   TLS 검증을 끄지 않는다. 현재 프로필에는 버킷 목록 조회 권한이 없어 대상 버킷명은 앱 설정으로 확인해야 한다.
6. Oracle 적용 대상(서버, 서비스명, 스키마)을 확인한다. 기존 DB에는 대상 확인 전 SQL을 실행하지 않는다.
7. 업로드·DB 반영 후 카드, 매장 상세, 시술 상세, 모델 모집 상세의 이미지와 크롭을 확인한다.

## 실행 순서

이미지 생성 → JPEG 저장 → 모든 파일 검사 → 객체별 UUID와 SHA-256 manifest 작성 → S3 업로드 및 조회 검사
→ 확인받은 시연 DB에 데이터·이미지 경로 반영 → 앱 이미지 API와 화면 확인 순서로 진행한다.

`upload-images.ps1`은 manifest 전체 파일·해시·5MB 제한을 먼저 검사한다.
`-Apply`가 없으면 업로드 없이 대상과 키 목록만 출력한다.
기존 객체는 덮어쓰거나 삭제하지 않으며 업로드 실패 시 DB를 변경하지 않는다.

```powershell
# 실제 버킷·리전·prefix는 배포 앱 설정과 일치하는 값을 직접 지정한다.
.\upload-images.ps1 -Bucket thankunnie-images-dev -Region ap-southeast-2 `
  -Prefix shop-images/ -Manifest .\image-manifest.json -Profile thankunnie

# 대상 및 파일 검사를 확인한 후 실제 업로드
.\upload-images.ps1 -Bucket thankunnie-images-dev -Region ap-southeast-2 `
  -Prefix shop-images/ -Manifest .\image-manifest.json -Profile thankunnie -Apply
```

위 manifest는 모든 실제 파일이 준비된 뒤 만든다. 없는 파일을 가리키는 manifest를 완료 산출물로 만들지 않는다.
부분 업로드 후 재실행하면 원격 SHA-256 메타데이터가 같은 키는 건너뛴다. 기존 객체의 메타데이터가 다르면 덮어쓰지 않고 중단한다.
업로드 스크립트는 설치된 PowerShell 7에서 실행하며 기본 동시 실행 수는 4다.
전체 성공 시에만 manifest 옆에 `.s3-verification.json`을 쓴다. 이미지 연결 SQL 생성기는 이 검증 기록을 요구한다.

현재 `03_demo_data_dml.sql`은 local H2 시작 시 `02_init_data_dml.sql` 다음에 자동 로드된다.
Oracle은 `01_schema_ddl.sql` → `02_init_data_dml.sql` → `03_demo_data_dml.sql` 순서로 수동 적용한다.
기존 03 파일은 재실행 시 고정 PK가 충돌한다. 기존 DB에 전체 파일을 재실행하거나 cleanup SQL을 자동 실행하지 않는다.
UNNIE-164의 추가 SQL은 기존 시연 데이터와 충돌하지 않도록 별도로 검토한 뒤 반영해야 한다.

신규 블록은 `SEQ_*.NEXTVAL`을 사용하고 자연 식별자 조건으로 재삽입을 건너뛴다.
기존 03 본문을 이미 적용한 DB에는 `demo164-additions.sql`만 사용한다.
이 파일도 대상 DB를 확인받기 전에는 실행하지 않는다. 자동 COMMIT은 하지 않는다.
`verify-data.sql`로 개수·구별 배분·가격·시간·모델 조건 등을 확인한 다음 사람이 COMMIT한다.
주소는 OSM 공개 주소 지점을 기준으로 하며 실제 입점 정보가 아니다. 출처·원본 ID는 `location-sources/`에 남긴다.
사진 공개 전에 실제 영업 매장으로 오인되지 않도록 가상 매장 안내를 유지한다.

재생성·검증 순서는 다음과 같다.

```powershell
node .\build-demo-sql.mjs
.\build-image-manifest.ps1
.\upload-images.ps1 -Bucket thankunnie-images-dev -Region ap-southeast-2 `
  -Manifest .\image-manifest.json -Profile thankunnie -Apply
node .\build-image-links.mjs
.\verify-h2.ps1 -RequireImages
```

필요한 환경에서 업로드 명령에 `-CaBundle`을 추가한다. `build-image-links.mjs`는 확인받은 버킷·리전·prefix와
전체 200개 객체의 검증 기록이 맞을 때만 기존 `03_demo_data_dml.sql`과 추가 전용 SQL에 이미지 경로를 연결한다.
추가 SQL에는 모델 시술 20개를 노출하기 위한 OPEN 시간대도 포함한다. 2026-11-02~11-20 사이 실제 영업일 814개다.
날짜 범위를 변경할 때는 SQL 생성기를 검토·재생성하고 적용된 시간대를 임의로 삭제하지 않는다.
신규 매장 50개의 PASSWORD_HASH는 `CHANGE_ME` 더미여서 로그인할 수 없다. 기존 owner 계정의 인증 정보는 건드리지 않는다.

H2 검증은 기존 Gradle 캐시의 드라이버와 Java 21을 사용해 전용 메모리 DB를 새로 만든다.
스키마 → 초기 데이터 → 전체 시연 데이터 → 집계 검증 → 신규 블록 재실행 → 재검증 → 시퀀스 신규 삽입을 확인한다.
H2 통과는 Oracle 21c XE 실행 검증을 대신하지 않는다. Oracle이 준비되면 확인받은 전용 시연 스키마에서 별도로 실행한다.

구별·매장별 상세 수량은 [allocation.md](allocation.md), 매장 및 시술 검수 이미지는
[shop-preview.jpg](shop-preview.jpg), [treatment-preview.jpg](treatment-preview.jpg)에서 확인한다.
실제 실행한 명령과 결과, 적용 전 확인할 사항은 [verification.md](verification.md)에 정리했다.
별도 포트의 전용 H2 앱에서 매장 상세 50건과 이미지 API 200건을 요청해 모든 실제 JPEG 해시 일치까지 확인했다.

## 확인된 화면 제약

`frontend/src/constants/regions.js`를 사용하는 알림 지역 선택 UI에는 강남·서초·마포·성동·광진·송파 및 분당만 있다.
현재 홈 빈자리 검색에는 구별 선택 UI가 없다. 나머지 서울 구의 데이터도 5자리 REGION_CODE로 API 조회할 수 있다.
구별 선택 UI 확대는 이 데이터 작업과 별도다.
local Vite는 `/api` 프록시가 없어 탐색 카드의 이미지 URL을 기존 VITE_API_BASE_URL 기준으로 보정했다.
