---
name: backend-code-style
description: 땡잡은 언니 백엔드(backend/, Spring Boot) Java 코드를 작성·수정할 때 사용한다. 팀 공통 코드(ApiResponse, BusinessException, ErrorCode, BaseTimeEntity)를 쓰는 형식과 Controller·Service·Entity·DTO 기본 모양을 정리한 템플릿.
---

# 백엔드 공통 코드 형식 (땡잡은 언니)

이 스킬은 **공통 코드를 쓰는 형식**만 다룬다. 그 밖의 규칙은 `docs/convention/BACKEND_CONVENTION.md`가 원본이며, 충돌하면 컨벤션 문서가 우선한다.

## 1. 공통 코드 위치

```text
com.ucamp.app.common
├── response/   ApiResponse<T>, ErrorResponse
├── exception/  ErrorCode, BusinessException, GlobalExceptionHandler
└── entity/     BaseTimeEntity
```

- 도메인 코드는 `com.ucamp.app.domain.{도메인}/{controller,service,repository,entity,dto/request,dto/response}`에 둔다.
- `common/` 수정은 별도 이슈 + 백엔드 파트장 리뷰.

## 2. 응답: `ApiResponse<T>`

모든 JSON 응답 본문은 `ApiResponse`로 감싼다.

```java
@PostMapping
public ResponseEntity<ApiResponse<MemberDetailResponse>> signup(
        @Valid @RequestBody MemberSignupRequest request) {
    MemberDetailResponse response = memberService.signup(request);
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response));
}

@GetMapping("/{memberId}")
public ResponseEntity<ApiResponse<MemberDetailResponse>> getMember(@PathVariable Long memberId) {
    return ResponseEntity.ok(ApiResponse.success(memberService.getMember(memberId)));
}

@DeleteMapping("/{memberId}")
public ResponseEntity<Void> deleteMember(@PathVariable Long memberId) {
    memberService.deleteMember(memberId);
    return ResponseEntity.noContent().build();   // 204는 본문 없음
}
```

- 성공: `ApiResponse.success(data)` → `{ "success": true, "data": ..., "error": null, "timestamp": ... }`
- 실패 응답은 직접 만들지 않는다. 예외를 던지면 `GlobalExceptionHandler`가 `ApiResponse.fail(...)`로 바꾼다.
- 상태 코드: 조회 200, 생성 201, 수정 200, 삭제 204.

## 3. 예외: `BusinessException` + `ErrorCode`

```java
Member member = memberRepository.findById(memberId)
        .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
```

- 예상 가능한 업무 실패는 `throw new BusinessException(ErrorCode.XXX)`. 메시지를 바꿔야 하면 `new BusinessException(ErrorCode.XXX, "상세 메시지")`.
- Controller·Service에서 try-catch로 삼키지 않는다. 로깅은 `GlobalExceptionHandler`에서 한 번만 한다.
- 요청 검증 실패(`@Valid`), JSON 파싱 실패, 파라미터 타입 오류는 핸들러가 `COMMON_400`으로 처리하므로 따로 잡지 않는다.

### ErrorCode 추가

```java
// ErrorCode.java — 도메인별로 묶어서 추가
MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_001", "회원을 찾을 수 없습니다."),
MEMBER_DUPLICATE_EMAIL(HttpStatus.CONFLICT, "MEMBER_002", "이미 가입된 이메일입니다."),
```

- 코드 형식은 `{도메인}_{3자리 번호}`.
- 추가하면 **같은 PR에서 `docs/api/error-codes.md`에도** 기재한다.

## 4. 엔티티: `BaseTimeEntity` 상속

```java
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "MEMBER")
public class Member extends BaseTimeEntity {

    @Id
    @Column(name = "MEMBER_ID")
    private Long id;   // ID 생성 방식은 팀 확정 후 적용

    @Column(name = "EMAIL", nullable = false)
    private String email;

    @Builder
    private Member(String email) {
        this.email = email;
    }

    public void changeEmail(String email) {
        this.email = email;
    }
}
```

- 모든 엔티티는 `BaseTimeEntity`를 상속한다. `createdAt`, `updatedAt`은 직접 넣지 않는다(JPA Auditing이 채움).
- Lombok은 `@Getter`, `@NoArgsConstructor(access = PROTECTED)`, 생성자에 `@Builder`만. `@Setter`, `@Data` 금지.
- 상태 변경은 의미 있는 메서드(`changeEmail()`, `cancel()`)로 제공한다.
- 연관관계는 `FetchType.LAZY`를 명시한다.

## 5. Service와 DTO 변환

```java
@Service
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public MemberDetailResponse getMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        return MemberDetailResponse.from(member);
    }

    @Transactional
    public MemberDetailResponse signup(MemberSignupRequest request) {
        // 검증 → 엔티티 생성 → 저장 → DTO 변환
    }
}
```

```java
public record MemberSignupRequest(@NotBlank @Email String email) {
}

public record MemberDetailResponse(Long memberId, String email) {

    public static MemberDetailResponse from(Member member) {
        return new MemberDetailResponse(member.getId(), member.getEmail());
    }
}
```

- 클래스에 `@Transactional(readOnly = true)`, 쓰기 메서드에만 `@Transactional`.
- Controller에는 엔티티 대신 Response DTO를 반환한다. 변환은 DTO의 `from(...)` 정적 팩토리.
- DTO 이름은 `{도메인}{용도}Request` / `{도메인}{용도}Response`.

## 6. 체크리스트

- [ ] 응답은 `ApiResponse<T>`, 204는 본문 없음
- [ ] 업무 실패는 `BusinessException(ErrorCode.XXX)`, 새 ErrorCode는 `docs/api/error-codes.md`에도 반영
- [ ] 엔티티는 `BaseTimeEntity` 상속, 요청·응답에 엔티티 직접 노출 없음
- [ ] `backend/`에서 `.\gradlew.bat test`(macOS/Linux `./gradlew test`) 통과
