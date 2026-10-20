# Back-end 개발 컨벤션 (Spring Boot)

> 적용 범위: `backend/` 디렉토리 전체
> 기술 스택: Java 21, Spring Boot 3.5, Spring Data JPA / MyBatis, Oracle 21c XE, Maven
> 개발 도구: Eclipse IDE for Enterprise Java (EGit 내장, Spring Tools 4)
> 상위 문서: `docs/git-guide.md` (Git 기반 팀 협업 및 형상관리) — 브랜치/커밋/PR 규칙은 해당 문서를 따르며 본 문서는 백엔드 코드 작성 규칙에 집중함.

---

## 1. 프로젝트 구조 및 패키지 규칙

```
backend/
├── src/main/java/com/ucamp/app/
│   ├── Application.java              # 진입점 (루트 패키지에 위치, 이동 금지)
│   ├── config/                       # Security, WebMvc, CORS, DB, Swagger 설정
│   ├── common/                       # 공통 응답 DTO, 예외 핸들러, 유틸 (파트장 관리)
│   │   ├── response/                 # ApiResponse, ErrorResponse
│   │   ├── exception/                # BusinessException, ErrorCode, GlobalExceptionHandler
│   │   └── util/
│   ├── controller/                   # REST API 엔드포인트
│   ├── service/                      # 비즈니스 로직 (인터페이스 + Impl 분리는 선택)
│   ├── repository/                   # JPA Repository 인터페이스 / MyBatis Mapper 인터페이스
│   ├── entity/                       # JPA 엔티티 (테이블 1:1 매핑)
│   └── dto/                          # 요청/응답 전송 객체
│       ├── request/
│       └── response/
└── src/main/resources/
    ├── application.yml               # 공통 설정 (Git 추적)
    ├── application-local.yml.example # 로컬 설정 템플릿 (Git 추적)
    ├── application-local.yml         # 개인 로컬 설정 (Git 제외 — 절대 커밋 금지)
    └── mapper/                       # MyBatis SQL 매퍼 XML
```

### 1.1 패키지 배치 원칙
- **레이어 단위 패키지**를 기본으로 하되, 도메인이 커지면 `controller/member`, `service/member` 처럼 하위 도메인 패키지로 분리함.
- `common/`은 **파트장이 초기 설계 후 커밋**하며, 수정이 필요하면 별도 이슈를 발행하고 파트장 리뷰를 받음.
- 레이어 간 의존 방향은 **단방향**으로 고정: `Controller → Service → Repository`. Controller에서 Repository 직접 호출 금지, Service에서 다른 Controller 호출 금지.

---

## 2. 네이밍 규칙

| 대상 | 규칙 | 예시 |
|---|---|---|
| 패키지 | 소문자, 단어 연결 시 구분자 없음 | `com.ucamp.app.member` |
| 클래스 / 인터페이스 | PascalCase, 역할 접미사 필수 | `MemberController`, `MemberService`, `MemberRepository` |
| JPA 엔티티 | PascalCase 단수형, 접미사 없음 | `Member`, `Order`, `OrderItem` |
| DTO | `{도메인}{용도}Request` / `{도메인}{용도}Response` | `MemberSignupRequest`, `MemberDetailResponse` |
| MyBatis Mapper | `{도메인}Mapper` (인터페이스), XML은 동일 이름 | `MemberMapper.java`, `mapper/MemberMapper.xml` |
| 메서드 | camelCase, 동사로 시작 | `findById`, `createOrder`, `validateStock` |
| 변수 / 필드 | camelCase, 의미 있는 전체 단어 (약어 지양) | `memberId`, `orderStatus` (`mId`, `ordSt` 금지) |
| 상수 | UPPER_SNAKE_CASE | `MAX_CART_ITEMS`, `TOKEN_EXPIRATION_MS` |
| boolean 필드 | `is` 접두사 없이 형용사/상태명 | `active`, `deleted` (getter는 `isActive()`) |
| 테스트 클래스 | `{대상클래스}Test` | `MemberServiceTest` |
| 테스트 메서드 | `{행위}_{조건}_{기대결과}` 또는 한글 `@DisplayName` 병행 | `signup_duplicateEmail_throwsException` |

### 2.1 DB 객체 네이밍 (Oracle)
- 테이블: **UPPER_SNAKE_CASE 복수형 금지, 단수형** → `MEMBER`, `ORDER_ITEM` (`ORDER`는 예약어이므로 `ORDERS` 사용)
- 컬럼: UPPER_SNAKE_CASE → `MEMBER_ID`, `CREATED_AT`
- PK: `{테이블}_ID`, 시퀀스: `SEQ_{테이블}`, 인덱스: `IDX_{테이블}_{컬럼}`, FK: `FK_{자식테이블}_{부모테이블}`
- 엔티티 필드 ↔ 컬럼 매핑은 Spring Boot 기본 `SpringPhysicalNamingStrategy`(camelCase → snake_case)에 맡기고, 예외가 필요한 경우에만 `@Column(name = ...)` 명시.

---

## 3. 레이어별 작성 규칙

### 3.1 Controller
- 클래스에 `@RestController` + `@RequestMapping("/api/v1/{리소스복수형}")` 사용. 버전 접두사 `/api/v1`은 모든 엔드포인트에 고정.
- Controller는 **요청 검증(`@Valid`) → Service 호출 → 응답 래핑**만 담당. 비즈니스 로직, 트랜잭션, 엔티티 직접 노출 금지.
- 응답은 항상 `ApiResponse<T>` 공통 포맷으로 반환.
- HTTP 메서드와 상태 코드 매핑:

| 동작 | 메서드 | 성공 코드 |
|---|---|---|
| 목록/단건 조회 | `GET` | 200 |
| 생성 | `POST` | 201 |
| 전체 수정 | `PUT` | 200 |
| 부분 수정 | `PATCH` | 200 |
| 삭제 | `DELETE` | 204 (또는 200 + 공통 응답) |

```java
@RestController
@RequestMapping("/api/v1/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MemberDetailResponse>> signup(
            @Valid @RequestBody MemberSignupRequest request) {
        MemberDetailResponse response = memberService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}
```

### 3.2 Service
- 클래스에 `@Service`, 쓰기 작업 메서드에 `@Transactional`, 읽기 전용은 `@Transactional(readOnly = true)`를 클래스 레벨 기본값으로 두고 쓰기 메서드만 오버라이드.
- Service는 **엔티티를 반환하지 않고 Response DTO로 변환하여 반환**함. 변환 로직은 DTO의 정적 팩토리(`MemberDetailResponse.from(member)`)에 둠.
- 예외는 `BusinessException(ErrorCode.XXX)`로 통일하여 던지고, `GlobalExceptionHandler`가 공통 에러 응답으로 변환.
- 다른 도메인 Service 호출은 허용하되 순환 의존이 생기면 즉시 설계 재검토 (파트장 상의).

### 3.3 Repository / Mapper
- **단순 CRUD·조건 조회는 JPA**, 복잡한 통계·다중 조인·동적 SQL은 **MyBatis**로 분리.
- JPA 메서드명 쿼리는 조건 2개 이하까지만 허용. 그 이상은 `@Query` 또는 MyBatis로 전환.
- MyBatis XML: `namespace`는 Mapper 인터페이스 FQCN, `id`는 메서드명과 일치. `resultType`은 DTO 또는 Map 사용, 엔티티 직접 매핑 금지.
- N+1 발생 가능 지점은 `@EntityGraph` 또는 `fetch join`으로 처리하고 PR 설명에 명시.

### 3.4 Entity
- `@Entity` 클래스는 **`protected` 기본 생성자 필수** (JPA 스펙 + 역직렬화 호환).
- 모든 엔티티는 `BaseTimeEntity`(`createdAt`, `updatedAt`, `@EnableJpaAuditing`)를 상속.
- Setter 전체 공개 금지. 상태 변경은 의미 있는 메서드(`member.changePassword(...)`, `order.cancel()`)로 제공.
- 연관관계 기본값은 `FetchType.LAZY`. `@ManyToOne(fetch = EAGER)` 금지.
- `@Data`, `@ToString`에 연관 엔티티 포함 금지 (순환 참조·LazyInitialization 방지).
- Lombok을 사용할 경우 `@Getter`, `@NoArgsConstructor(access = PROTECTED)`, `@Builder`까지만 허용.

### 3.5 DTO
- 요청 DTO는 `record` 또는 불변 클래스로 작성하고 `jakarta.validation` 어노테이션(`@NotBlank`, `@Email`, `@Size`)을 필드에 선언.
- 응답 DTO는 엔티티 필드를 그대로 노출하지 않고 화면에 필요한 값만 담음. 비밀번호·토큰·내부 ID 노출 금지.
- 엔티티를 Controller 요청/응답 타입으로 직접 사용하는 것은 **리뷰 반려 사유**.

---

## 4. 공통 응답 및 예외 처리

### 4.1 공통 응답 포맷
```json
{
  "success": true,
  "data": { ... },
  "error": null,
  "timestamp": "2026-10-20T23:00:00"
}
```
실패 시:
```json
{
  "success": false,
  "data": null,
  "error": { "code": "MEMBER_001", "message": "이미 가입된 이메일입니다." },
  "timestamp": "2026-10-20T23:00:00"
}
```

### 4.2 ErrorCode 규칙
- `enum ErrorCode { ... }`에 `HttpStatus`, `code`, `message`를 함께 정의.
- 코드 형식: `{도메인}_{3자리 번호}` → `MEMBER_001`, `ORDER_002`, `AUTH_001`, `COMMON_500`.
- 새 ErrorCode 추가 시 `docs/api/error-codes.md`에도 동일하게 기재 (프론트엔드 팀과 공유).

### 4.3 예외 처리
- `GlobalExceptionHandler`(`@RestControllerAdvice`)에서 `BusinessException`, `MethodArgumentNotValidException`, 그 외 `Exception`을 각각 처리.
- Controller/Service에서 `try-catch`로 예외를 삼키지 않음. 로깅 후 재던지기도 금지 (핸들러에서 한 번만 로깅).
- 스택 트레이스를 클라이언트 응답에 포함하지 않음.

---

## 5. 설정 및 환경 분리

- `application.yml`: 공통 설정만. DB 접속 정보는 `${DB_URL:...}` 형태의 환경변수 플레이스홀더로 작성.
- `application-local.yml`: 개인 DB 계정, JWT 시크릿 등 민감 정보. **`.gitignore` 대상이며 Staging에 올라오면 즉시 Unstage.**
- 신규 설정 키 추가 시 반드시 `application-local.yml.example`에도 키와 더미 값을 추가하여 커밋.
- 프로파일은 `local`, `test`, `prod` 3종만 사용.
- `ddl-auto`는 `validate` 고정. `create`, `update` 사용 금지 — 스키마 변경은 아래 6절 절차를 따름.

---

## 6. DB 스키마 변경 규칙 (단일 진실 공급원)

- 스키마의 유일한 기준은 `docs/erd/01_schema_ddl.sql`, 초기 데이터는 `docs/erd/02_init_data_dml.sql`.
- 엔티티(`entity/*.java`) 또는 매퍼 XML의 컬럼 구조를 변경하는 커밋은 **반드시 같은 피처 브랜치에서 `01_schema_ddl.sql`을 함께 수정**하고 PR에 포함.
- DDL 변경 PR은 PR 템플릿 "세부 변경 사항"에 `[DB]` 태그로 변경된 테이블/컬럼을 명시하고, 리뷰어는 로컬 Oracle에 적용 후 `validate` 기동 성공을 확인한 뒤 Approve.
- DDL 병합 후 전 팀원은 `git pull` 직후 변경 스크립트를 로컬 DB에 수동 반영 (ORA-00904, ORA-00942 방지).

---

## 7. 코드 스타일

- 인코딩 UTF-8, 들여쓰기 **공백 4칸**, 탭 금지. 한 줄 최대 120자.
- 줄바꿈은 저장소 기준 LF (`core.autocrlf true` + `.gitattributes`로 자동 처리).
- 중괄호는 K&R 스타일 (여는 중괄호를 같은 줄에).
- import 순서: `java.*` → `jakarta.*` → `org.springframework.*` → 기타 라이브러리 → `com.ucamp.*`. 와일드카드(`*`) import 금지.
- 필드 주입(`@Autowired` 필드) 금지. **생성자 주입**만 사용 (`final` 필드).
- `System.out.println` 금지. 로깅은 `Slf4j Logger` 사용, 레벨은 `debug`(개발 흐름) / `info`(주요 이벤트) / `warn`(복구 가능) / `error`(장애).
- 매직 넘버·문자열은 상수 또는 `enum`으로 추출.
- 주석: 공개 API 클래스/메서드에는 Javadoc(`/** */`), 복잡한 로직에는 "왜"를 설명하는 한 줄 주석. 주석 처리된 폐기 코드는 커밋 전 삭제.
- Eclipse 포맷터: `docs/convention/eclipse-formatter.xml`을 팀 공통으로 Import (Window → Preferences → Java → Code Style → Formatter).

---

## 8. 테스트

- 위치: `src/test/java` 하위에 메인 소스와 동일한 패키지 구조.
- Service 단위 테스트: JUnit 5 + Mockito, Repository는 Mock 처리. 핵심 비즈니스 로직은 테스트 필수.
- Repository 테스트: `@DataJpaTest` (테스트 프로파일에서 H2 또는 로컬 Oracle 사용).
- Controller 테스트: `@WebMvcTest` + `MockMvc`.
- 테스트 메서드에는 `@DisplayName("한글 설명")` 부여.
- PR 생성 전 `mvnw test` 전체 통과 필수. 실패 테스트가 있는 PR은 리뷰 대상에서 제외.

---

## 9. API 명세 및 프론트엔드 협업

- 모든 엔드포인트는 Swagger(springdoc-openapi) 어노테이션으로 문서화하고, `/swagger-ui.html`에서 확인 가능해야 함.
- 신규 API 또는 응답 구조 변경 시 `docs/api/`에 명세를 갱신하고 **프론트엔드 파트장에게 PR 리뷰어로 지정**하여 사전 공유.
- 응답 JSON 키는 camelCase. 날짜는 ISO-8601 문자열(`yyyy-MM-dd'T'HH:mm:ss`).
- CORS 허용 Origin은 `config/WebConfig`에서 `http://localhost:5173`(Vite 기본 포트)으로 관리.

---

## 10. Git / PR 연계 규칙 (요약)

- 브랜치: `feature/be/{이슈번호}-{기능명}`, 버그는 `fix/be/{이슈번호}-{결함명}`.
- 커밋 scope: `member`, `auth`, `order`, `product`, `payment`, `config`, `common` 등 도메인/패키지명.
  - 예: `feat(auth): JWT 토큰 발급 및 검증 필터 구현`, `fix(order): 주문 수량 음수 검증 추가`
- Eclipse 메타파일(`.project`, `.classpath`, `.settings/`, `bin/`, `target/`)이 Git Staging에 보이면 **절대 Stage하지 않고** `.gitignore` 적용 상태를 확인.
- `git pull` 후 `pom.xml`이 변경되었으면 `Maven → Update Project (Alt+F5)` + `Force Update of Snapshots/Releases` 실행.
- PR 리뷰 체크 항목 (리뷰어):
  - [ ] Controller에 비즈니스 로직 없음
  - [ ] 엔티티가 요청/응답에 직접 노출되지 않음
  - [ ] 예외가 `BusinessException` + `ErrorCode`로 통일됨
  - [ ] 엔티티 변경 시 `01_schema_ddl.sql` 동반 수정됨
  - [ ] 민감 정보(`application-local.yml`, 토큰, 비밀번호)가 diff에 없음
  - [ ] 테스트 코드 포함 및 통과
  - [ ] `System.out.println`, 주석 처리된 폐기 코드 없음
