# A-02 메인페이지 연결 — 이슈 #26

상태: **메인 측 구현 및 검증 완료 · C 실제 화면/API 통합 대기**
작성일: 2026-10-23
작업 브랜치: `feature/fe/26-main-page`

## 범위 및 선행 작업

목록·가격·상태를 표시하고 카드 클릭 시 C 상세 화면으로, 조건 선택 후 탐색 시 C 탐색 화면으로 전달한다. 메인 전용 조회 API를 만들지 않고 A-01 store/API 어댑터를 재사용한다.

최신 dev(`c824326`)에서 새 브랜치를 만들고, 아직 dev에 병합되지 않은 A-01의 `547f315`까지 반영했다. 이 브랜치는 #24의 두 커밋에 의존한다. #24가 먼저 dev에 반영되면 #26 비교 범위에서 A-01 변경이 제외되는지 확인한다.

## C와 맞출 임시 연결 계약

현재 dev에는 C 탐색·상세 화면 및 탐색 컨트롤러가 없다. 아래 경로와 추가 필드는 A-02의 임시 계약이며 C 담당자의 기존 구현을 확인한 결과가 아니다.

| 동작 | 대상 | 전달 내용 |
| --- | --- | --- |
| 카드 전체 클릭 | `/items/:id` | 서버가 반환한 양의 정수 ID |
| 오늘/마감 임박 더보기 | `/explore` | A-01 해당 프리셋의 모든 필터와 정렬, page=0 |
| 날짜·정렬 선택 후 ‘조건으로 탐색’ | `/explore` | 선택 날짜의 startDate/endDate, sort, availableOnly=true, page=0, size=6 |

상세 경로에는 가격·상태를 넘기지 않는다. C 상세는 ID로 최신 정보를 조회해야 한다. 링크는 React Router Link를 사용해 키보드 이동과 새 탭 열기를 지원한다.

추가한 경로 상수는 `src/constants/routes.js`에 있다. **현재 AppRouter에는 이 경로의 C 화면이 등록되어 있지 않으므로 지금 클릭하면 기존 NotFound 화면이 표시된다.** C 화면 구현 후 이 상수에 맞춰 등록하거나 상수 값을 실제 경로로 변경해야 한다. 공통 AppRouter를 수정하거나 C 화면을 대신 구현하지 않았다.

### 탐색 쿼리 예시

```text
/explore?availableOnly=true&startDate=2026-10-25&endDate=2026-10-25&sort=CLOSING_ASC&page=0&size=6
/explore?availableOnly=true&closingWithinHours=24&sort=CLOSING_ASC&page=0&size=6
```

선택 날짜가 비어 있으면 날짜 범위를 보내지 않는다. 정렬의 ‘마감 임박순’ 선택은 CLOSING_ASC 정렬만 지정하며 24시간 제한을 자동으로 추가하지 않는다. ‘마감 임박’ 영역의 더보기에는 A-01에 정의된 24시간 제한을 유지한다.

필터는 URLSearchParams로 직렬화한다. 이전 페이지 번호는 전달하지 않고 0으로 초기화한다. 지원 쿼리 외 내부 값은 URL에 넣지 않는다. 조건 선택은 폼에서 하고 탐색 버튼으로 적용한다.

## 카드 표시 계약

기존 A-01의 id, title, startsAt, applicationClosesAt 외에 아래 필드를 C와 협의해야 한다.

| 필드 | 임시 타입 | 표시 |
| --- | --- | --- |
| shopName | string, 선택 | 매장명 |
| price | number, 원화 판매가 | 한국 원화 형식. 0도 유효한 가격 |
| originalPrice | number, 선택 | 판매가보다 높을 때만 취소선 정상가 |
| status | string | 아래 상태명 매핑 |

| 상태 | 화면 표시 |
| --- | --- |
| AVAILABLE | 예약 가능 |
| HELD | 임시 확보 중 |
| BOOKED | 예약 완료 |
| SOLD_OUT | 매진 |
| CLOSED | 마감 |
| CANCELLED | 취소 |

가격 누락·문자열·음수·NaN 등은 ‘가격 확인 필요’, 알 수 없는 상태는 ‘상태 확인 필요’로 표시한다. 일정 파싱 실패도 안내 문구를 표시하며 화면을 깨뜨리지 않는다. 잘못된 ID에는 링크를 생성하지 않는다. 상태만으로 상세 접근을 차단하지 않으며 실제 예약 가능 여부는 C 상세·예약 API가 판단한다.

## 검증

다음 명령을 frontend에서 실행했다.

- `node --test tests/main.test.mjs tests/main-navigation.test.mjs tests/main-render.test.mjs`: 16개 통과
- `npm run lint`: 오류·경고 없이 통과
- `npm run build`: 통과

테스트는 프리셋/선택 조건 전달, 가격·상태 누락 처리, 상세 href, 카드 전체 링크 렌더, 오류·빈 결과 독립 표시를 포함한다. 렌더 테스트는 실제 React와 MemoryRouter, Vite SSR 변환을 사용했다. 브라우저 클릭·실제 C 화면 표시·실제 HTTP/DB 필터링은 아직 검증하지 않았다.

## C 통합 시 확인

- [ ] 실제 상세 리소스 ID와 경로가 일치한다.
- [ ] 실제 탐색 경로를 공통 라우터에 등록하고 URL 쿼리를 초기 필터로 읽는다.
- [ ] 가격·상태·시각 필드를 위 임시 계약과 맞추거나 어댑터에서 변환한다.
- [ ] 메인 카드 클릭 → 실제 C 상세 표시를 확인한다.
- [ ] 조건 선택·더보기 → 실제 C 탐색 결과 및 뒤로 가기를 브라우저에서 확인한다.
