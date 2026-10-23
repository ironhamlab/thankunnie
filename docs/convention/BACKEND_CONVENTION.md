# Back-end 개발 컨벤션 (Spring Boot)

> 적용 범위: `backend/` 디렉토리 전체
> 기술 스택: Java 21, Spring Boot 3.5, Spring Data JPA, Gradle
> DB: 개발·테스트 H2(MODE=Oracle), 운영 Oracle 21c XE, 로컬 Oracle 확인용 Docker Oracle Free
> 개발 도구: IntelliJ IDEA
> 상위 문서: `docs/git-guide.md` — 브랜치·커밋·PR 규칙은 해당 문서를 따르며, 본 문서는 백엔드 코드 작성 규칙에 집중한다. 중복된 내용이 충돌하면 상위 문서를 우선한다.
> 코드 형식 템플릿: `backend-code-style` 스킬. 스킬과 본 문서가 충돌하면 본 문서를 우선한다.

---

## 1. 프로젝트 구조 및 패키지 규칙

```text
backend/
├── src/main/java/com/ucamp/app/
│   ├── Application.java              # 진입점: 루트 패키지에 위치, JVM 시간대 고정
│   ├── config/                       # Security, WebMvc, CORS, DB, Swagger, TimeConfig(Clock·Auditing)
│   ├── common/                       # 공통 응답 DTO, 예외 핸들러, 유틸
│   │   ├── response/                 # ApiResponse, ErrorResponse
│   │   ├── exception/                # BusinessException, ErrorCode, GlobalExceptionHandler
│   │   ├── entity/                   # BaseTimeEntity
│   │   ├── converter/                # BooleanToYnConverter
│   │   └── util/                     # TimeUtils
│   └── domain/
│       └── {도메인}/
│           ├── controller/           # REST API 엔드포인트
│           ├── service/              # 비즈니스 로직
│           ├── repository/           # JPA Repository 인터페이스
│           ├── entity/               # JPA 엔티티, 도메인 enum
│           └── dto/
│               ├── request/
│               └── response/
└── src/main/resources/
    ├── application.yml               # 공통 설정: Git 추적
    ├── application-local.yml.example # 로컬 설정 템플릿: Git 추적
    ├── application-local.yml         # 개인 로컬 설정: Git 제외
    └── schema.sql                    # H2용 스키마: 빌드 시 docs/erd/01_schema_ddl.sql에서 복사 (직접 수정 금지)
```

### 1.1 패키지 배치 원칙

- **도메인 단위 패키지**를 기본으로 하고, 각 도메인 내부에서 Controller, Service, Repository, Entity, DTO를 분리한다.
- `common/`, `config/`는 파트장이 초기 설계 후 커밋하며, 수정이 필요하면 별도 이슈를 발행하고 파트장 리뷰를 받는다.
- 요청 처리의 기본 호출 방향은 `Controller → Service → Repository`로 한다.
- Controller에서 Repository를 직접 호출하거나 Service에서 Controller를 호출하지 않는다.
- 새 기능은 해당 테이블을 다루는 기존 도메인 패키지에 작성한다. 새 도메인 패키지는 팀과 합의 후 만든다.
- 회원 도메인은 `user`로 하며 `member`를 사용하지 않는다.

---

## 2. 네이밍 규칙

| 대상 | 규칙 | 예시 |
|---|---|---|
| 패키지 | 소문자, 단어 연결 시 구분자 없음 | `com.ucamp.app.domain.reservation` |
| 역할 클래스·인터페이스 | PascalCase + 역할 접미사 | `ReservationController`, `ReservationService`, `ReservationRepository` |
| JPA 엔티티 | PascalCase 단수형, 접미사 없음 | `Reservation`, `Treatment`, `ShopScheduleException` |
| DTO | `{도메인}{용도}Request` / `{도메인}{용도}Response` | `TreatmentCreateRequest`, `ReservationDetailResponse` |
| 메서드 | camelCase, 동사로 시작 | `findById`, `createReservation`, `validateHoldExpired` |
| 변수·필드 | camelCase, 의미 있는 전체 단어 사용 | `reservationId`, `holdExpiresAt` (`rId`, `hExp` 지양) |
| 상수 | UPPER_SNAKE_CASE | `HOLD_MINUTES`, `MAX_DEPOSIT_AMOUNT` |
| boolean 필드 | `is` 접두사 없이 형용사·상태명 | `active`, `closed` |
| 테스트 클래스 | `{대상클래스}Test` | `RefundCalculatorTest` |
| 테스트 메서드 | `{행위}_{조건}_{기대결과}` | `cancel_afterStart_throwsException` |

- 테스트 메서드에는 한글 `@DisplayName`을 함께 작성한다.
- primitive boolean의 일반 getter는 `isActive()`, record 접근자는 `active()` 형태를 사용한다.

### 2.1 DB 객체 네이밍 (Oracle)

- 테이블: **UPPER_SNAKE_CASE 단수형**
  - 예: `RESERVATION`, `SHOP_SCHEDULE_EXCEPTION`
  - 예약어는 피하고 대체 이름을 사용한다.
    - `USER` → `APP_USER`. 이 경우 PK 컬럼은 `USER_ID`를 유지한다(FK 컬럼명과 일치시키기 위한 예외).
- 컬럼: UPPER_SNAKE_CASE
  - 예: `RESERVATION_ID`, `CREATED_AT`
- PK 컬럼: `{테이블}_ID` (위 `APP_USER` 예외 및 `schema.sql`에 이미 정의된 이름은 그대로 사용)
- 시퀀스: `SEQ_{테이블}`
- 인덱스: `IDX_{테이블}_{컬럼}`
- UNIQUE 제약조건: `UK_{테이블}_{컬럼}`
- FK 제약조건: `FK_{자식테이블}_{부모테이블}`
  - 같은 테이블 간 FK가 여러 개면 역할명을 덧붙여 구분한다.
- 엔티티 필드의 camelCase → snake_case 변환은 기본 네이밍 전략에 맡긴다.
- 실제 DB 이름이 기본 변환 결과와 다르면 `@Table(name = ...)`, `@Column(name = ...)`을 명시한다.

---

## 3. 레이어별 작성 규칙

### 3.1 Controller

- 클래스에 `@RestController`와 `@RequestMapping("/api/v1/{리소스복수형}")`을 사용한다.
- 업무 API의 버전 접두사는 `/api/v1`로 통일한다. Swagger 등 프레임워크 제공 경로는 제외한다.
- Controller는 **요청 검증(`@Valid`) → Service 호출 → 응답 구성**을 담당한다.
- 비즈니스 로직, 트랜잭션 처리, 엔티티 직접 노출을 금지한다.
- JSON 응답 본문은 `ApiResponse<T>` 공통 포맷을 사용한다.
- `204 No Content` 응답에는 본문을 반환하지 않는다.

| 동작 | 메서드 | 성공 코드 |
|---|---|---|
| 목록·단건 조회 | GET | 200 |
| 생성 | POST | 201 |
| 전체 수정 | PUT | 200 |
| 부분 수정 | PATCH | 200 |
| 삭제 | DELETE | 기본 204, 결과 데이터가 필요하면 200 + 공통 응답 |

```java
@RestController
@RequestMapping("/api/v1/treatments")
public class TreatmentController {

  private final TreatmentService treatmentService;

  public TreatmentController(TreatmentService treatmentService) {
    this.treatmentService = treatmentService;
  }

  @PostMapping
  public ResponseEntity<ApiResponse<TreatmentDetailResponse>> createTreatment(
          @Valid @RequestBody TreatmentCreateRequest request) {
    TreatmentDetailResponse response = treatmentService.createTreatment(request);
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response));
  }
}
```

### 3.2 Service

- 클래스에 `@Service`를 선언한다. 인터페이스와 구현체 분리는 선택 사항이다.
- 클래스에 `@Transactional(readOnly = true)`를 기본으로 선언하고, 쓰기 작업 메서드에 `@Transactional`을 선언한다.
- Controller에 반환하는 결과는 엔티티 대신 Response DTO로 변환한다.
- 단순 변환 로직은 DTO의 정적 팩토리에 둔다.
  - 예: `TreatmentDetailResponse.from(treatment)`
- Service 간 내부 호출의 반환 타입은 용도에 맞게 정한다.
- 예상 가능한 업무 예외는 `BusinessException(ErrorCode.XXX)`으로 표현하고, `GlobalExceptionHandler`에서 공통 에러 응답으로 변환한다.
- 다른 도메인 Service 호출은 허용하되, 순환 의존이 생기면 파트장과 설계를 재검토한다.
- 현재 시각이 필요한 Service는 `Clock`을 생성자로 주입받는다(5.1절).
- 현재 사용자 ID·역할·매장 ID는 인증 공통 방식(B-01)으로만 꺼낸다. 각자 임시 방식을 만들지 않는다.

### 3.3 Repository

- 데이터 접근은 Spring Data JPA를 사용한다.
- 단순 CRUD와 조건 조회는 기본 제공 메서드 또는 메서드명 쿼리를 사용한다.
- 메서드명 쿼리의 조건은 2개 이하를 원칙으로 하며, 그 이상은 `@Query` 등으로 작성한다.
- 복잡한 통계·다중 조인 조회는 `@Query`와 DTO 프로젝션 등을 사용한다.
- N+1 문제가 발생하는 조회는 `@EntityGraph` 또는 fetch join 등으로 처리하고 PR 설명에 명시한다.
- 동시성 제어가 필요한 조회(예: 예약 점유 시 매장 잠금)는 `@Lock(LockModeType.PESSIMISTIC_WRITE)`를 사용하고 PR 설명에 명시한다.

### 3.4 Entity

- `@Entity` 클래스에는 JPA를 위한 **protected 기본 생성자**를 둔다.
- 테이블·컬럼 이름과 타입은 `docs/erd/01_schema_ddl.sql`과 일치시킨다.
- 모든 엔티티는 `createdAt`, `updatedAt`을 정의한 `BaseTimeEntity`를 상속한다.
  - `UPDATED_AT`이 없는 테이블은 예외로 하며 PR에 명시한다.
- `BaseTimeEntity`에는 다음을 선언한다.
  - `@MappedSuperclass`
  - `@EntityListeners(AuditingEntityListener.class)`
  - `createdAt`에 `@CreatedDate`
  - `updatedAt`에 `@LastModifiedDate`
- `@EnableJpaAuditing`은 별도의 `@Configuration` 클래스에 선언하고, `Clock` 기반 `DateTimeProvider`를 지정한다(`dateTimeProviderRef`).
- PK는 `SEQ_{테이블}` 시퀀스로 발급한다.
  - `@GeneratedValue(strategy = GenerationType.SEQUENCE)` + `@SequenceGenerator(sequenceName = "SEQ_{테이블}", allocationSize = 1)`
  - `IDENTITY`, `AUTO`, `UUID`는 사용하지 않는다.
- 복합 PK는 `@IdClass`로 매핑하고 `Persistable`을 구현한다. 구현하지 않으면 `save()`가 insert 대신 merge로 동작해 PK 중복이 감지되지 않는다.
- 문자열 코드 컬럼(`STATUS`, `CANCEL_TYPE` 등)은 enum으로 매핑하고 `@Enumerated(EnumType.STRING)`을 명시한다. enum 상수는 DDL 주석의 값과 철자를 일치시킨다.
- `CHAR(1)` Y/N 컬럼은 `boolean` 필드로 두고 `common/converter/BooleanToYnConverter`를 사용한다.
- Setter 전체 공개를 금지한다. 상태 변경은 의미 있는 메서드로 제공한다.
  - 예: `treatment.deactivate()`, `reservation.confirm(...)`
- 연관관계는 `FetchType.LAZY`를 원칙으로 한다. `@ManyToOne`, `@OneToOne`에도 명시한다.
- 엔티티에 Lombok `@Data`를 사용하지 않으며, `toString`에 연관 엔티티를 포함하지 않는다.
- 엔티티에서 Lombok은 `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`, `@Builder`를 사용한다.
- `@Builder`는 생성에 필요한 값을 받는 생성자에 적용하여 기본 생성자 및 자동 생성 필드와 충돌하지 않도록 한다.

### 3.5 DTO

- 요청 DTO는 record 또는 불변 클래스로 작성한다.
- `jakarta.validation` 어노테이션을 일반 클래스의 필드 또는 record 컴포넌트에 선언한다.
  - 예: `@NotBlank`, `@NotNull`, `@Positive`, `@Size`
- 응답 DTO에는 화면과 API 사용에 필요한 값만 담는다.
- 응답 DTO의 시각 필드는 `OffsetDateTime`으로 하고 `TimeUtils.toOffset(...)`으로 변환한다.
- 금액·환불액 등 서버가 계산하는 값은 요청 DTO로 받지 않거나, 받더라도 서버에서 다시 검증한다.
- 비밀번호, 비밀번호 해시, 서버 시크릿은 응답에 포함하지 않는다.
- 인증 토큰은 인증 API에서 정한 방식으로만 전달한다.
- ID는 API 사용에 필요한 경우 반환하되, 불필요한 내부 관리용 식별자는 노출하지 않는다.
- 엔티티를 Controller 요청·응답 타입으로 직접 사용하는 것은 리뷰 반려 사유다.

---

## 4. 공통 응답 및 예외 처리

### 4.1 공통 응답 포맷

성공 시:

```json
{
  "success": true,
  "data": {
    "reservationId": 1
  },
  "error": null,
  "timestamp": "2026-10-21T09:00:00+09:00"
}
```

실패 시:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "RESERVATION_001",
    "message": "예약 가능한 시간이 아닙니다."
  },
  "timestamp": "2026-10-21T09:00:00+09:00"
}
```

- 실패 시에는 오류에 맞는 HTTP 상태 코드를 사용한다.
- 204 응답에는 공통 응답 본문을 포함하지 않는다.

### 4.2 ErrorCode 규칙

- `enum ErrorCode`에 `HttpStatus`, `code`, `message`를 함께 정의한다.
- 코드 형식: `{도메인}_{3자리 번호}`
  - 예: `RESERVATION_001`, `CANCEL_002`, `AUTH_001`, `COMMON_500`
- 새 ErrorCode 추가 시 `docs/api/error-codes.md`에도 동일하게 기재하여 프론트엔드와 공유한다.

### 4.3 예외 처리

- `GlobalExceptionHandler`에 `@RestControllerAdvice`를 선언한다.
- 업무 예외, 요청 검증 실패, 그 외 예외를 구분하여 처리한다.
- JSON 파싱 실패, 파라미터 타입 불일치 등 잘못된 요청이 모두 500으로 처리되지 않도록 한다.
- 예상하지 못한 시스템 예외는 500으로 처리한다.
- Controller와 Service에서 예외를 삼키지 않는다.
- 복구 또는 업무 예외로의 변환이 필요한 경우에만 예외를 잡는다.
  - 예: 무료 취소 PK 중복(`DataIntegrityViolationException`) → `BusinessException(ErrorCode.CANCEL_GRACE_ALREADY_USED)`
- 로깅 후 재던지기로 동일한 예외를 중복 기록하지 않는다. 최종 예외 처리 지점에서 한 번 로깅한다.
- 스택 트레이스와 내부 시스템 정보를 클라이언트 응답에 포함하지 않는다.

---

## 5. 설정 및 환경 분리

- `application.yml`에는 공통 설정만 둔다.
- DB 접속 정보 등 환경별 설정은 환경변수 플레이스홀더 또는 로컬 설정으로 관리한다.
- 비밀번호와 JWT 시크릿에 실제 값을 기본값으로 넣지 않는다.
  - 예: `${DB_PASSWORD}`, `${JWT_SECRET}`
- `application-local.yml`은 개인 DB 계정 등 로컬 설정을 담으며 **Git 커밋을 금지**한다.
- 신규 환경 설정 키를 추가하면 `application-local.yml.example`에도 키와 더미 값을 추가한다.
- 프로파일은 `local`, `test`, `prod`를 사용한다.
- `ddl-auto`는 `validate`로 고정한다.
- `create`, `update` 등 자동 스키마 변경 설정은 사용하지 않는다.
- 스키마 변경은 6절 절차를 따르며, 테스트 DB도 실행 전에 스키마를 준비한다.
- DB 연결 설정은 프로파일별로 분리한다.
  - local: H2 (MODE=Oracle)
  - test: H2 (MODE=Oracle)
  - prod: Oracle 21c XE
- local·test의 H2는 앱 시작 시 `schema.sql`로 스키마를 생성한다.
  - `spring.sql.init.mode=always`로 설정한다.
  - `spring.jpa.defer-datasource-initialization`은 사용하지 않는다. 스키마 생성이 엔티티 검증보다 먼저 실행되어야 한다.
- 로컬에서 Oracle 동작을 확인할 때는 Docker Oracle Free를 사용한다. 운영 Oracle 21c XE와 버전이 다르므로, 버전 차이에 영향을 받는 기능은 21c XE에서도 확인한다.
- H2의 Oracle 호환 모드가 Oracle의 모든 동작을 보장하지는 않으므로, Oracle 전용 기능은 Oracle에서도 확인한다.

### 5.1 시간 처리

- 기준 시간대는 `Asia/Seoul`이다. JVM 기본 시간대는 `Application.main()`에서 고정한다.
- 현재 시각·날짜는 주입받은 `Clock`으로 구한다.
  - 예: `LocalDateTime.now(clock)`, `LocalDate.now(clock)`
  - 인자 없는 `now()`, `new Date()`, `System.currentTimeMillis()`를 사용하지 않는다.
- 쿼리에서 `SYSDATE`, `SYSTIMESTAMP`, `CURRENT_TIMESTAMP`를 사용하지 않는다. 기준 시각은 파라미터로 전달한다.
- 엔티티의 시각 필드는 `LocalDateTime`, 날짜만 필요한 필드는 `LocalDate`를 사용한다.
- 응답 DTO의 시각 필드는 `OffsetDateTime`(+09:00)으로 변환해 반환한다. (9절 ISO-8601 규칙)
- 시간 구간은 시작 포함·끝 제외 `[start, end)`로 처리한다.
- 날짜·마감·만료 경계가 있는 로직은 `Clock.fixed(...)`로 경계값 테스트를 작성한다.

---

## 6. DB 스키마 변경 규칙

- 현재 스키마의 기준은 `docs/erd/01_schema_ddl.sql`, 초기 데이터의 기준은 `docs/erd/02_init_data_dml.sql`로 한다.
- `src/main/resources/schema.sql`은 빌드 시 복사되는 사본이므로 직접 수정하지 않는다.
- 테이블·컬럼·제약조건 등 실제 스키마를 변경하면 같은 피처 브랜치에서 관련 코드와 스키마 SQL을 함께 수정하고 PR에 포함한다.
- DDL은 Oracle과 H2(MODE=Oracle) 양쪽에서 동작하는 문법만 사용한다.
- 엔티티 메서드·주석 변경처럼 스키마에 영향이 없는 수정은 DDL 수정 대상이 아니다.
- DDL 변경 PR은 세부 변경 사항에 `[DB]` 태그로 변경된 테이블·컬럼을 명시한다.
- 기존 DB에 적용할 변경 SQL과 적용 시 주의사항을 PR에 함께 기록한다.
- 다른 팀원의 작업에 영향을 주는 변경은 병합 전에 공유하고 적용 순서를 조율한다.
- 리뷰어는 로컬 Oracle에 변경을 적용하고 `validate` 기동 성공을 확인한 뒤 Approve한다.
- 병합 후 팀원은 변경 내용을 확인하여 자신의 로컬 Oracle DB에 수동 반영한다. H2는 재기동 시 자동 반영된다.

---

## 7. 코드 스타일

- 인코딩은 UTF-8, 들여쓰기는 **공백 4칸**, 한 줄 최대 길이는 120자로 한다.
- 탭은 사용하지 않는다.
- 줄바꿈은 저장소의 `.gitattributes`를 기준으로 LF로 통일한다. Windows 실행용 `.bat` 파일은 CRLF를 허용한다.
- 중괄호는 K&R 스타일로 작성한다.
- import 순서:
  - `java.*`
  - `jakarta.*`
  - `org.springframework.*`
  - 기타 라이브러리
  - `com.ucamp.*`
- 와일드카드 import는 사용하지 않는다.
- 필드 주입은 금지하고 **생성자 주입**을 사용한다. 주입받는 필드는 final로 선언한다.
- `System.out.println` 대신 SLF4J Logger를 사용한다. Lombok `@Slf4j`도 허용한다.
- 로그 레벨:
  - debug: 개발 흐름
  - info: 주요 이벤트
  - warn: 복구 가능한 문제
  - error: 장애
- 업무 의미가 있거나 반복되는 숫자·문자열은 상수 또는 enum으로 추출한다.
  - 예: 점유 시간 5분, 예약금 상한 20,000원, 접수 마감 20분
- 공개 API 클래스·메서드에는 Javadoc을 작성하고, 복잡한 로직에는 이유를 설명하는 주석을 작성한다.
- 주석 처리된 폐기 코드는 커밋 전에 삭제한다.
- IntelliJ 포맷 설정은 팀 공통으로 맞춘다. 공통 설정 파일은 `docs/convention/`에서 관리한다.

---

## 8. 테스트

- 위치는 `src/test/java` 아래로 하며 메인 소스와 동일한 패키지 구조를 사용한다.
- Service 단위 테스트는 JUnit 5와 Mockito를 사용하고 Repository는 Mock 처리한다.
- 핵심 비즈니스 로직은 테스트를 작성한다.
  - 특히 가격·환불 계산, 가용 시간 계산, 점유·만료·취소 상태 전이는 경계값 테스트를 포함한다.
- 시각에 의존하는 테스트는 `Clock.fixed(...)`를 주입한다. 실제 현재 시각에 의존하는 테스트를 작성하지 않는다.
- Repository 테스트는 `@DataJpaTest`를 사용한다.
- 테스트 DB는 H2(`schema.sql` 자동 생성) 또는 별도 Oracle 테스트 계정을 사용하고, Oracle을 쓰면 스키마 준비 방법을 실행 안내에 기록한다.
- 개인 개발 데이터와 테스트 데이터가 섞이지 않도록 한다.
- H2 테스트만으로 Oracle 전용 SQL이나 시퀀스 동작 검증을 대체하지 않는다.
- Oracle을 사용하는 `@DataJpaTest`는 `@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)`으로 DB 자동 교체를 막는다.
- Controller 테스트는 `@WebMvcTest`와 MockMvc를 사용한다.
- 테스트 메서드에는 `@DisplayName("한글 설명")`을 작성한다.
- PR 생성 전 `backend/`에서 전체 테스트를 실행한다.
  - macOS/Linux: `./gradlew test`
  - Windows: `.\gradlew.bat test`
- 실패 테스트가 있는 PR은 테스트를 수정한 뒤 리뷰를 요청한다.

---

## 9. API 명세 및 프론트엔드 협업

- 모든 업무 API는 Swagger(springdoc-openapi)로 문서화하고, 설명이 필요한 부분은 어노테이션으로 보완한다.
- 로컬 환경의 `/swagger-ui.html`에서 명세를 확인할 수 있어야 한다.
- 신규 API 또는 요청·응답 구조 변경 시 `docs/api/`의 관련 명세를 갱신한다.
- 프론트엔드에 영향을 주는 변경은 프론트엔드 파트장을 PR 리뷰어로 지정하여 공유한다.
- 응답 JSON 키는 camelCase를 사용한다.
- 날짜만 나타내는 값은 `yyyy-MM-dd` 형식을 사용한다.
- 날짜와 시각을 나타내는 값은 시간대 오프셋을 포함한 ISO-8601 문자열을 사용한다.
  - 예: `2026-10-21T09:00:00+09:00`
- CORS 허용 Origin은 `config/WebConfig` 등 공통 설정에서 관리한다.
- 로컬 Origin은 `http://localhost:5173`을 사용하며, 운영 Origin은 환경 설정으로 분리한다.

---

## 10. Git / PR 연계 규칙 (요약)

상세 규칙은 `docs/git-guide.md`와 `AGENTS.md`를 따른다.

- 브랜치:
  - 기능: `feature/be/{이슈번호}-{기능명}`
  - 버그: `fix/be/{이슈번호}-{결함명}`
  - 구조·성능 개선: `refactor/be/{이슈번호}-{작업명}`
  - 서버·Docker·배포 설정: `infra/{이슈번호}-{작업명}`
  - 이슈번호는 Jira 키의 숫자 부분을 사용한다. `UNNIE-30` → `30`
- dev의 최신 변경은 작업 브랜치에서 `git pull origin dev`로 받는다. rebase는 사용하지 않는다.
- 커밋 메시지는 scope 없이 `type: subject` 형식으로 작성한다. 이슈 완료 시 본문에 `Resolves: UNNIE-{번호}`를 적는다.
- 커밋 예시:
  - `feat: 예약 점유 API 구현`
  - `fix: 자정 경계 환불액 계산 오류 수정`
- `.gradle/`, `build/`, `out/`, `*.iml` 등 로컬 캐시·빌드 산출물은 Git에서 제외한다.
- `.idea/`의 개인 설정은 제외하고, 팀 공통 설정은 합의한 파일만 추적한다.
- Gradle Wrapper(`gradlew`, `gradlew.bat`, `gradle/wrapper/`)는 Git으로 관리한다.
- `git pull` 후 Gradle 빌드 설정이 변경되면 IntelliJ에서 Gradle 프로젝트를 동기화한다.

### PR 리뷰 체크리스트

- [ ] Controller에 비즈니스 로직이 없음
- [ ] 엔티티가 요청·응답에 직접 노출되지 않음
- [ ] 업무 예외가 `BusinessException`과 `ErrorCode`로 표현됨
- [ ] 엔티티의 테이블·컬럼이 `01_schema_ddl.sql`과 일치함
- [ ] PK는 `SEQ_{테이블}` + `allocationSize = 1`, enum 필드는 `@Enumerated(EnumType.STRING)`
- [ ] 인자 없는 `now()` 호출과 쿼리의 DB 시각 함수가 없음 (`Clock` 주입 사용)
- [ ] 응답 DTO의 시각 필드가 `OffsetDateTime`
- [ ] 실제 스키마 변경 시 DDL 수정과 기존 DB 적용 SQL이 포함됨
- [ ] 민감 정보가 diff에 없음
- [ ] 동작 변경에 필요한 테스트가 추가·수정되고 전체 테스트가 통과함
- [ ] `System.out.println`, 주석 처리된 폐기 코드가 없음