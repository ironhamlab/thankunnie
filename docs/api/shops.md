# A-03 매장 관리 (#27)

- `GET /api/v1/shops/{shopId}`: 본인 매장 정보 조회, 200
- `PUT /api/v1/shops/{shopId}`: 본인 매장 정보 전체 저장·수정, 200
- 인증 사용자의 `requireShopOwner`로 조회·수정 모두 본인 매장만 허용한다. 고객·관리자·다른 매장 사장님은 403, 비로그인은 401이다.
- 매장이 없으면 404, 필수값 누락·길이 초과·잘못된 좌표·존재하지 않는 카테고리는 400이다.
- 응답은 `ApiResponse<ShopDetailResponse>`이며 로그인 정보는 노출하지 않는다.

## 요청 예시

```json
{
  "categoryId": 1,
  "name": "언니 미용실",
  "regionCode": "11680",
  "address": "서울특별시 강남구 테헤란로 1",
  "addressDetail": "2층",
  "latitude": 37.4979000,
  "longitude": 127.0276000,
  "phone": "02-000-0000",
  "description": "매장 소개",
  "notice": "예약 후 방문해 주세요",
  "imageUrl": null
}
```

응답 data는 요청 필드에 `shopId`가 추가된다. 위도는 -90~90, 경도는 -180~180이며 소수점 7자리까지 허용한다. 필수 필드는 categoryId, name, regionCode, address, latitude, longitude, phone이다. 선택 필드는 생략하거나 null로 보내면 지워진다. 단, 대표 이미지는 별도 API로 관리한다. 프로필 수정 시 imageUrl은 생략하거나 null만 허용하며 기존 이미지를 유지한다. URL을 직접 지정하면 400이다.

## A-07 대표 이미지 (UNNIE-46)

| 메서드·경로 | 용도 | 성공 응답 |
| --- | --- | --- |
| `PUT /api/v1/shops/me/image` | 본인 매장 대표 이미지 업로드·교체 | 200, `ApiResponse<ShopImageResponse>` |
| `DELETE /api/v1/shops/me/image` | 본인 매장 대표 이미지 삭제 | 204, 본문 없음 |
| `GET /api/v1/shops/{shopId}/images/{fileName}` | 현재 대표 이미지 공개 조회 | 200, `image/jpeg` 또는 `image/png` 바이너리 |

- API-114(PUT)·API-115(DELETE)의 매장은 인증 주체의 shopId로 결정한다.
- PUT은 `multipart/form-data`의 `file` 필드로 파일 1개를 보낸다. 여러 파일을 같은 필드로 전송하거나 multipart boundary가 없는 손상 요청은 400으로 거부한다. JSON URL 요청은 받지 않는다.
- JPEG(`.jpg`, `.jpeg`, `image/jpeg`)·PNG(`.png`, `image/png`)만 허용한다. 확장자 대소문자는 구분하지 않는다.
- 최대 파일 크기는 **5MiB(5 × 1024 × 1024바이트), 경계값 포함**, 최대 해상도는 **1,600만 화소**다. 전체 multipart 요청 한도는 6MiB다.
- 누락·빈 파일·크기 초과·형식 불일치·실제 이미지 디코딩 실패는 `400 / COMMON_400`이다. 파일 이름과 MIME 타입뿐 아니라 실제 이미지 형식과 내용을 확인한다.
- PUT·DELETE는 비로그인 401, 고객·관리자 403, 존재하지 않는 본인 매장 404다. 이미지가 없는 매장에 DELETE를 반복해도 204다.
- 업로드 성공 예시: `data: {"shopId": 1, "imageUrl": "/api/v1/shops/1/images/{서버에서 생성한 파일명}.png"}`. 프론트는 API 서버 기준 상대 경로를 사용한다. GET 매장 정보의 imageUrl도 같은 경로를 반환한다.
- 조회 API는 현재 DB에 연결된 이미지만 제공한다. 교체·삭제된 URL이나 잘못된 파일명은 404다. 응답에 `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`를 설정한다.

### 실패 시 유지 및 동시 수정

1. 권한과 매장 존재를 확인한 뒤, DB 트랜잭션과 행 잠금 없이 파일을 검증하고 새 이름으로 저장한다.
2. 파일 저장 후 짧은 쓰기 트랜잭션을 시작하고, 새 파일의 롤백 정리를 등록한 뒤 SHOP 행 잠금을 획득한다. 최신 URL을 다시 읽고 SHOP.IMAGE_URL을 변경한다. 잠금·재조회·DB 저장 실패 시 새 파일을 정리한다. 트랜잭션 시작 자체가 실패해도 새 파일을 정리한다. 검증·파일 저장 실패 시 기존 URL과 파일을 유지한다.
3. DB 커밋 후 기존 파일을 삭제한다. DB 롤백 시 기존 URL·파일을 유지하고 새 파일을 정리한다.
4. 이미지 교체·삭제 및 매장 프로필 수정은 같은 SHOP 행의 `PESSIMISTIC_WRITE` 잠금을 사용한다. 동시 요청이 기존 URL을 덮어쓰거나 서로의 파일을 지우지 않도록 직렬화한다.

기존 외부 URL은 교체·삭제 시 연결만 해제하며 외부 파일에는 접근하지 않는다. 파일 정리 실패는 별도 DB 트랜잭션의 정리 작업으로 기록하며 이미 커밋된 결과는 유지한다. 강제 종료 등 기록 전에 중단된 경우에는 운영 점검이 필요하다.

### S3 저장 및 설정 (UNNIE-46 추가)

- 기본 저장소는 **Amazon S3**다. AWS SDK for Java 2.x를 사용하며 파일 검증에는 기존 JDK ImageIO를 사용한다. 대표 이미지 자체는 기존 SHOP.IMAGE_URL을 사용하며 삭제 실패 재시도용 SHOP_IMAGE_CLEANUP 테이블을 추가한다.
- 업로드 흐름은 `프론트 → 백엔드 검증 → S3 PutObject → DB 커밋 → 이전 객체 DeleteObject`다. 기존 API 주소와 DB의 상대 이미지 조회 경로는 유지한다.
- S3 객체 키는 기본 `shop-images/{서버에서 생성한 파일명}`이다. 원본 파일명을 키로 쓰지 않는다. `Content-Type`과 파일 길이를 함께 저장하며 공개 ACL을 설정하지 않는다.
- 버킷은 비공개로 두고 Public Access Block을 유지한다. GET 이미지 API에서 백엔드가 S3 GetObject로 읽어 바이너리를 반환하므로 프론트에는 AWS 키·버킷 주소·만료 URL을 전달하지 않는다. S3 브라우저 직접 업로드를 사용하지 않으므로 버킷 CORS 설정은 필요하지 않다.
- S3 업로드 실패는 `500 / COMMON_500`이며 기존 DB URL·객체는 유지한다. 새 객체를 저장한 뒤 응답만 유실된 경우도 새 키 삭제를 시도한다. S3 조회 404는 `404 / COMMON_404`, 권한·인증·연결 실패는 내부 정보 없는 `500 / COMMON_500`으로 반환한다.
- DB 롤백 시 새 S3 객체를 정리하고, DB 커밋 후 기존 객체를 삭제한다. 정리 실패는 아래 영속 재시도 작업으로 처리한다. 버킷 버전 관리가 켜져 있으면 DeleteObject는 삭제 마커를 만들 수 있으며 과거 버전 보존은 버킷 Lifecycle 정책으로 관리한다.

| 환경변수 | 설정 키 | 기본값·설명 |
| --- | --- | --- |
| `SHOP_IMAGE_STORAGE` | `app.shop-image.storage` | `s3`; 오프라인 개발에는 `local` 선택 |
| `SHOP_IMAGE_S3_BUCKET` | `app.shop-image.s3.bucket` | 빈 값; 사람이 생성한 버킷 이름 지정 |
| `AWS_REGION` | `app.shop-image.s3.region` | `ap-northeast-2`; 실제 버킷 리전과 일치시킬 것 |
| `SHOP_IMAGE_S3_PREFIX` | `app.shop-image.s3.prefix` | `shop-images/`; 최대 300자, 영문·숫자로 시작하며 영문·숫자·`/`·`_`·`-` 허용 |

키·버킷 설정 없이도 애플리케이션 기동은 가능하지만 S3 이미지 업로드·조회는 AWS 연결 준비 후 사용할 수 있다. 버킷 미설정 시 이미지 요청은 명확한 500 오류를 반환하며 로컬 저장소로 자동 전환하지 않는다.

자격 증명은 AWS SDK 기본 공급자 체인을 사용한다. 로컬에서는 사람이 **백엔드를 실행하는 프로세스의 환경변수**에 `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`를 직접 설정한다. 임시 자격 증명은 `AWS_SESSION_TOKEN`도 설정한다. 프로젝트 `.env` 파일은 Spring Boot가 자동으로 읽지 않으므로 IDE 실행 설정 또는 실행 셸 환경변수를 사용한다. EC2/ECS 배포에서는 IAM 역할을 사용할 수 있다. 실제 키를 코드·example·채팅에 기록하지 않는다.

서버 IAM 권한은 해당 버킷의 이미지 prefix에 대한 `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject`가 필요하다. 버킷은 자동 생성하지 않는다. SSE-KMS를 사용하는 버킷은 별도 KMS 권한도 준비해야 한다.

연결 후 확인 순서: 정상 파일 업로드 → 이미지 URL 공개 조회 → 교체 후 과거 URL 404 → 삭제 후 404 → 잘못된 파일 업로드 시 기존 이미지 유지. 자동화 테스트는 Mock S3Client와 H2를 사용하며 실제 AWS 권한·버킷 연결은 검증하지 않는다.

참고: [AWS 기본 자격 증명 공급자 체인](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html).

### 삭제 실패 재시도 및 DB 변경

- 삭제 실패한 파일명·저장소 식별자(scope)를 `SHOP_IMAGE_CLEANUP`에 기록한다. `REQUIRES_NEW` 트랜잭션을 사용하므로 원래 이미지 저장이 롤백되어도 기록은 유지된다. 같은 scope·파일명은 중복 등록하지 않는다.
- 기존 `app.scheduling.enabled=true`일 때 1분 주기로 최대 20개 작업을 조회한다. 첫 재시도는 1분 뒤이며 실패할 때마다 대기를 2·4·8·16·32·60분으로 늘리고 이후 최대 1시간 간격으로 계속 재시도한다. 성공하면 작업을 삭제한다.
- 작업 행의 비관적 잠금으로 다중 서버 중복 실행을 제어한다. 현재 DB에서 참조하는 이미지는 삭제하지 않고 정리 작업만 취소한다. 버킷·prefix·로컬 경로가 다른 작업은 현재 저장소에서 실행하지 않는다.
- S3 삭제 후 정리 작업의 DB 삭제가 실패하면 작업이 남아 다음에 같은 키를 다시 삭제한다. 파일이 이미 없는 경우에도 삭제는 성공으로 처리한다.
- S3 삭제와 정리 기록용 DB가 동시에 실패하면 자동 재시도를 보장할 수 없다. `매장 이미지 정리 작업 기록 실패, 수동 정리 필요` 오류 로그의 scope·파일명을 기준으로 복구해야 한다. 기록 전 프로세스가 강제 종료된 경우도 이 재시도 기능의 보장 범위 밖이다. 전체 S3 버킷을 임의로 훑어 삭제하는 기능은 추가하지 않았다.
- 테스트는 실제 H2 제약 위반으로 JPA 커밋을 실패시켜 기존 URL 유지·새 S3 객체 삭제를 확인한다. S3도 삭제 실패하도록 만들었을 때 별도 트랜잭션 기록·후속 재시도까지 검증한다. S3는 Mock이며 실제 AWS 장애를 재현한 것은 아니다.

신규 스키마의 `SHOP_IMAGE_CLEANUP` 테이블·시퀀스·인덱스는 `docs/erd/01_schema_ddl.sql`에 정의되어 있다. 이 객체가 없는 기존 Oracle DB는 애플리케이션 배포 전에 해당 정의를 적용해야 하며, 적용 SQL과 순서는 관련 PR 본문에 기록한다. 기존 SHOP 데이터 변경·삭제는 없다. H2 테스트는 신규 스키마에서 이미지 정리 기능을 검증하며 Oracle 실DB 확인은 별도로 필요하다.

### 로컬 파일 저장소 및 전환 주의사항

- AWS 없이 개발할 때는 `SHOP_IMAGE_STORAGE=local`을 지정한다. 이때만 `app.shop-image.directory`를 사용하며 기본값은 `${user.home}/.thankunnie/shop-images`다. example 파일에 관련 설정을 반영했다.
- 기존 로컬 파일은 S3로 자동 이전하지 않는다. 기존 이미지를 유지하려면 기존 파일명을 그대로 지정 prefix에 복사한 뒤 S3로 전환하거나, 전환 후 다시 업로드해야 한다.
- 저장소·버킷·prefix를 바꾸면 기존 URL이 가리키는 객체를 찾지 못할 수 있으므로 데이터가 있는 환경에서는 이전을 먼저 준비한다.
- `local` 환경도 서버가 생성한 파일명만 파일 접근에 사용한다. 여러 서버에서 local을 사용하면 공유 영속 볼륨이 필요하다.
- 실제 인증 정책을 적용할 때는 현재 대표 이미지의 GET 경로를 공개 조회 대상으로 유지한다. PUT·DELETE에는 기존 B-01 인증 주체와 매장 권한 검사를 적용한다.
- 이번 범위는 백엔드 API이며 프론트 업로드 화면 연결은 포함하지 않는다.

## 계정 및 스키마 전제

현재 SHOP 테이블은 매장과 사장님 로그인 계정이 한 행이며 필수 프로필을 함께 가진다. 이 API는 이미 생성된 사장님 계정에 연결된 매장 프로필을 저장한다. 새 로그인 계정 생성·비밀번호 설정은 인증/가입 기능의 범위이며 이 API에서 처리하지 않는다. OWNER 인증 주체의 shopId와 실제 SHOP 행이 먼저 준비되어 있어야 한다.

A-03 매장 프로필의 기존 테이블은 유지하며, A-07 이미지 정리 재시도를 위해 위 마이그레이션 테이블을 추가한다. CATEGORY도 사전에 준비되어 있어야 한다. local/test에서는 `X-Test-User: OWNER_1`(shopId=1), `OWNER_2`(shopId=2)로 권한 차단을 확인할 수 있다. 테스트 사용자 헤더는 DB 행을 자동 생성하지 않는다.
