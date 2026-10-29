# 시술 상세 프론트 연결 (UNNIE-49)

## 조회 흐름

- 화면 → `useServiceDetailStore.loadDetail` → `serviceDetailApi.getDetail` → 공통 `axiosInstance`.
- `GET /api/v1/explore/vacancies/{treatmentId}?startsAt={KST 시작 시각}`을 호출한다.
- 인증 없는 고객용 C-03 API를 사용한다. 사장님 전용 시술 단건 API는 사용하지 않는다.
- 상세 조회가 실패하면 시연 데이터로 대체하지 않는다.

## 목록·지도 담당 연결 방법

기존 라우트 `/services/:id`에서 `id`는 실제 `treatmentId`다. 선택한 빈자리의 시작 시각을
쿼리로 함께 전달한다. 같은 시술도 시간에 따라 가격과 예약 가능 여부가 달라진다.

```jsx
const query = new URLSearchParams({ startsAt: vacancy.startsAt });
const to = `${generatePath(ROUTES.SERVICE_DETAIL, { id: vacancy.treatmentId })}?${query}`;
```

`startsAt`은 KST 기준 `2026-10-30T15:00:00` 또는 서버 응답의
`2026-10-30T15:00:00+09:00`을 받는다. `+`가 공백이 되지 않도록 `URLSearchParams`로 인코딩한다.
API에는 오프셋을 제거한 KST `LocalDateTime`을 보낸다.
시작 시각 또는 실제 시술 ID가 없는 이전 시연 주소(`/services/nail` 등)에서는
빈자리 선택 안내를 표시한다. 홈·검색의 실제 목록 연결은 담당 C 범위로 남아 있다.

## 표시 및 상태

- 매장명·소개·공지·주소·연락처, 시술 설명·소요 시간·모델 참여 조건·촬영 안내·준비 사항을 표시한다.
- 이미지가 없거나 불러오지 못하면 기존 기본 시술 일러스트를 표시한다.
- 정상가·할인율·판매가·예약금·잔금은 서버 값 그대로 표시한다. 20% 예약금 계산을 제거했다.
- 시작·종료·예약 마감 시각을 Asia/Seoul 기준으로 표시한다. 가짜 점유 타이머는 표시하지 않는다.
- 로딩, 조회 실패와 재시도, 404, 예약 마감·예약 불가를 구분한다.
- 이동·재조회 시 이전 응답이 뒤늦게 도착해 현재 상세를 덮어쓰지 않도록 요청 번호로 제어한다.

## 남은 연결

현재 `ReservationPage`와 `CheckoutPage`는 시연 데이터와 브라우저 내 점유를 사용한다.
실제 시술 ID를 이 흐름에 넘기지 않도록 예약 버튼은 비활성화하고 준비 중 안내를 표시한다.
예약 점유 API 및 다른 시간 선택 연결은 후속 담당 범위다.
상세 화면의 조회 성공만으로 예약·결제까지 검증됐다고 판단하지 않는다.

## 검증

- `frontend/`: `npm run lint`, `npm run build` 통과.
- `frontend/`: `node --input-type=module` 임시 검증 스크립트 통과. 기존 esbuild로 실제 모듈을
  묶고 Axios 어댑터에 준비된 응답을 주입해 요청 경로·파라미터, 이전 요청 무시,
  화면 이탈 후 응답 무시, 빈 응답 오류를 확인했다. React 서버 렌더링으로 성공·모델 조건·서버 금액,
  예약 마감·예약 불가·선택 누락·404 표시를 확인했다. 브라우저에서 실제 데이터 성공 조회를 검증한 것은 아니다.
- 로컬 백엔드 `GET /api/v1/explore/vacancies?size=1`: 200, 빈 목록.
- 로컬 백엔드 시술 ID 1 상세 조회: 404. 실제 데이터로 성공 화면을 확인하려면 활성 매장·시술 데이터가 필요하다.
- 백엔드 코드·스키마·공통 인증·라우터·의존성 변경은 없다.
