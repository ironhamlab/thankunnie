---
name: backend-code-style
description: 땡잡은 언니 백엔드(backend/, Spring Boot) Java 코드를 작성·수정할 때 사용한다. Controller·Service·Repository·Entity·DTO·ErrorCode·테스트를 팀 공통 코드(ApiResponse, BusinessException, ErrorCode, BaseTimeEntity)와 BACKEND_CONVENTION에 맞춰 작성하는 템플릿과 체크리스트.
---

# 백엔드 코드 스타일 (땡잡은 언니)

규칙 원본은 `docs/convention/BACKEND_CONVENTION.md`다. 이 스킬은 그 규칙을 **바로 따라 쓸 수 있는 템플릿**으로 정리한 것이며, 충돌하면 컨벤션 문서가 우선한다.

## 1. 패키지 위치

```text
com.ucamp.app
├── config/                 # Security, WebMvc(CORS), Swagger, JpaAuditing
├── common/
│   ├── response/           # ApiResponse, ErrorResponse
│   ├── exception/          # ErrorCode, BusinessException, GlobalExceptionHandler
│   └── entity/             # BaseTimeEntity
└── domain/{도메인}/
    ├── controller/  service/  repository/  entity/
    └── dto/request/  dto/response/
```

- 호출 방향은 `Controller → Service → Repository`만 허용한다.
- `common/` 수정은 별도 이슈 + 백엔드 파트장 리뷰.

## 2. Controller

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/slots")
public class SlotController {

    private final SlotService slotService;

    @PostMapping
    public ResponseEntity<ApiResponse<SlotDetailResponse>> createSlot(
            @Valid @RequestBody SlotCreateRequest request) {
        SlotDetailResponse response = slotService.createSlot(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/{slotId}")
    public ResponseEntity<ApiResponse<SlotDetailResponse>> getSlot(@PathVariable Long slotId) {
        return ResponseEntity.ok(ApiResponse.success(slotService.getSlot(slotId)));
    }

    @DeleteMapping("/{slotId}")
    public ResponseEntity<Void> deleteSlot(@PathVariable Long slotId) {
        slotService.deleteSlot(slotId);
        return ResponseEntity.noContent().build();
    }
}
```

- URL은 `/api/v1/{리소스 복수형}`.
- 하는 일은 **검증(`@Valid`) → Service 호출 → 응답 구성**뿐. 비즈니스 로직·트랜잭션 금지.
- 응답 본문은 항상 `ApiResponse<T>`. 엔티티를 그대로 반환하지 않는다.
- 상태 코드: 조회 200, 생성 201, 수정 200, 삭제 204(본문 없음).
- 목록은 `?page=0&size=20`(0부터)으로 받고 `ApiResponse<PageResponse<T>>`로 반환한다. Spring `Page`를 그대로 반환하지 않는다.
- 요청자는 헤더를 직접 읽지 않고 `@AuthenticationPrincipal`로 받는다(로그인 전에는 `X-USER-ID` 헤더 필터가 채워 줌).

## 3. Service

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SlotService {

    private final SlotRepository slotRepository;

    public SlotDetailResponse getSlot(Long slotId) {
        ShopSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SLOT_NOT_FOUND));
        return SlotDetailResponse.from(slot);
    }

    @Transactional
    public SlotDetailResponse createSlot(SlotCreateRequest request) {
        // 검증 → 엔티티 생성 → 저장 → DTO 변환
    }
}
```

- 클래스에 `@Transactional(readOnly = true)`, **쓰기 메서드에만** `@Transactional`.
- 반환은 Response DTO. 변환은 DTO의 `from(...)` 정적 팩토리.
- 예상 가능한 실패는 `throw new BusinessException(ErrorCode.XXX)`. try-catch로 삼키지 않는다.
- 같은 자원(슬롯 등)을 동시에 다투면 `@Lock(PESSIMISTIC_WRITE)`로 잠그고, 여러 행은 항상 같은 순서(시작 시각 오름차순)로 잠근다. 여러 개를 함께 확보할 땐 전부 확보 또는 예외로 전부 롤백.

## 4. Repository

```java
public interface SlotRepository extends JpaRepository<ShopSlot, Long> {

    List<ShopSlot> findByShopIdAndSlotStartAtBetween(Long shopId, LocalDateTime from, LocalDateTime to);
}
```

- 메서드명 쿼리는 조건 2개까지. 그 이상·조인·통계는 `@Query` + DTO 프로젝션.
- N+1이 생기면 `@EntityGraph`/fetch join으로 처리하고 PR에 적는다.

## 5. Entity

```java
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "SHOP_SLOT")
public class ShopSlot extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_SHOP_SLOT_GENERATOR")
    @SequenceGenerator(name = "SEQ_SHOP_SLOT_GENERATOR", sequenceName = "SEQ_SHOP_SLOT", allocationSize = 1)
    @Column(name = "SLOT_ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SHOP_ID", nullable = false)
    private Shop shop;

    @Column(name = "SLOT_START_AT", nullable = false)
    private LocalDateTime slotStartAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "SALE_STATUS", nullable = false, length = 10)
    private SaleStatus saleStatus;

    @Builder
    private ShopSlot(Shop shop, LocalDateTime slotStartAt) {
        this.shop = shop;
        this.slotStartAt = slotStartAt;
        this.saleStatus = SaleStatus.OPEN;
    }

    public void close() {
        this.saleStatus = SaleStatus.CLOSED;
    }
}
```

- 모든 엔티티는 `BaseTimeEntity` 상속(생성·수정일시 자동).
- Lombok은 `@Getter`, `@NoArgsConstructor(access = PROTECTED)`, 생성자에 `@Builder`만. **`@Setter`, `@Data` 금지**, `toString`에 연관 엔티티 금지.
- 상태 변경은 의미 있는 메서드(`close()`, `cancel()`)로.
- 연관관계는 `@ManyToOne`/`@OneToOne`까지 **`FetchType.LAZY` 명시**.
- 테이블·컬럼은 UPPER_SNAKE(`SHOP_SLOT`, `SLOT_START_AT`), PK는 `{테이블}_ID`, 시퀀스는 `SEQ_{테이블}`.
- enum은 `@Enumerated(EnumType.STRING)`(ORDINAL 금지). boolean 필드는 `is` 없이(`active`), 컬럼은 `NUMBER(1)`.
- 일시 필드는 `LocalDateTime`(KST).
- PK는 `SEQ_{테이블}` 시퀀스(`allocationSize = 1`), `IDENTITY` 사용 금지.
- `ddl-auto: validate`이므로 엔티티를 바꾸면 `docs/erd/01_schema_ddl.sql`(Oracle)과 `schema-h2.sql`(H2) **두 파일**을 같은 PR에서 수정한다.

## 6. DTO

```java
public record SlotCreateRequest(
        @NotNull Long shopId,
        @NotNull @Future LocalDateTime slotStartAt
) {
}

public record SlotDetailResponse(Long slotId, LocalDateTime slotStartAt, String saleStatus) {

    public static SlotDetailResponse from(ShopSlot slot) {
        return new SlotDetailResponse(slot.getId(), slot.getSlotStartAt(), slot.getSaleStatus().name());
    }
}
```

- 이름: `{도메인}{용도}Request` / `{도메인}{용도}Response`.
- 요청 DTO는 record + `jakarta.validation`. 응답에 비밀번호·해시·내부 식별자 금지.
- JSON 키는 camelCase, 날짜는 `yyyy-MM-dd`, 일시는 오프셋 없는 KST `2026-10-21T09:00:00` (`LocalDateTime`).

## 7. ErrorCode 추가

```java
SLOT_NOT_FOUND(HttpStatus.NOT_FOUND, "SLOT_001", "빈자리를 찾을 수 없습니다."),
SLOT_NOT_AVAILABLE(HttpStatus.CONFLICT, "SLOT_002", "이미 예약되었거나 판매가 종료된 빈자리입니다."),
```

- 코드 형식 `{도메인}_{3자리}`. 도메인별로 번호를 이어서 쓴다.
- 추가하면 **`docs/api/error-codes.md`에도 같은 PR에서** 기재한다.

## 8. 코드 스타일

- 공백 4칸(탭 금지), 한 줄 120자, LF, K&R 중괄호.
- import 순서: `java.*` → `jakarta.*` → `org.springframework.*` → 기타 → `com.ucamp.*`. 와일드카드 import 금지.
- **생성자 주입**은 `@RequiredArgsConstructor` + `final` 필드. 필드 주입(`@Autowired` 필드) 금지. `@Value` 파라미터가 필요할 때만 생성자 직접 작성.
- 로그는 SLF4J(`@Slf4j` 허용). `System.out.println` 금지. 예외 로그는 최종 처리 지점(GlobalExceptionHandler)에서 한 번만.
- 정책값(예약금 20,000원, 점유 5분, 마감 15/20/30분 등)은 상수·enum으로. 하드코딩 금지.
- 공개 API 클래스·메서드에 Javadoc, 복잡한 로직에는 "왜"를 설명하는 주석. 주석 처리된 폐기 코드는 지운다.

## 9. 테스트

```java
@ExtendWith(MockitoExtension.class)
class SlotServiceTest {

    @Test
    @DisplayName("이미 같은 시간 슬롯이 있으면 등록에 실패한다")
    void createSlot_duplicateStartAt_throwsException() {
    }
}
```

- 클래스 `{대상}Test`, 메서드 `{행위}_{조건}_{기대결과}` + 한글 `@DisplayName`.
- Service 단위 테스트는 Mockito로 Repository를 Mock. Repository는 `@DataJpaTest`, Controller는 `@WebMvcTest`.

## 10. 완료 전 체크리스트

- [ ] `backend/`에서 `.\gradlew.bat test`(macOS/Linux `./gradlew test`) 통과
- [ ] Controller에 비즈니스 로직 없음, 엔티티가 요청·응답에 노출되지 않음
- [ ] 새 ErrorCode → `docs/api/error-codes.md` 반영
- [ ] 엔티티·스키마 변경 → `docs/erd/01_schema_ddl.sql` + `schema-h2.sql` 반영
- [ ] 새 설정 키 → `application-local.yml.example` 반영
- [ ] 커밋 메시지 `type: 제목` (scope 없음, 한글 가능, AI 서명 없음)
