# 메인 기능 연결 (A-01 / A-02)

## UNNIE-56: 기본 메인 화면 디자인 통합

- 기본 경로 `/`는 `DesignApp`의 `DesignLayout`과 `DesignHomePage`를 렌더링한다. `/design`에서 확인하던 디자인·검색·지도·카드 구성을 그대로 사용한다.
- `/design`도 기존 링크 호환을 위해 같은 화면으로 유지한다. 홈 링크와 검색 후 이동은 `/`로 통일한다. 상세·예약·사장님 화면은 기존 `/design/...` 경로를 유지한다.
- 현재 홈의 목록은 `pages/design/data.js`의 `SERVICES`, 상태는 `useDesignStore`를 사용하는 시연 데이터다. 실제 API 연동 완료를 의미하지 않는다.
- 백엔드 연동 시 최종 홈인 `DesignHomePage`의 시연 데이터 경계를 교체한다. 기존 `pages/main/`, `useMainStore`, API 모듈과 검증 유틸은 재사용 검토를 위해 보존하며, 현재 기본 홈에서는 호출하지 않는다.
- 아래 A-01/A-02 설명과 포트 5179 검증 기록은 이전 API 메인 구현 이력이다. 현재 홈 연결 기준은 이 절을 따른다.

A-01은 조회 프리셋·store·API 어댑터를, A-02(#26)는 가격·상태 카드와 C 화면 링크 및 탐색 조건 전달을 구현한다.

## 현재 연결

`src/pages/home/HomePage.jsx`가 `useMainStore`를 MainPage에 전달한다. store는 기존 Zustand create와 공통 axiosInstance를 사용하며, 요청 경로는 아직 임시 `/v1/items`이다. 공통 응답의 data 추출과 오류 정규화는 기존 axiosInstance가 담당한다.

MainPage는 React Router 내부에서 렌더해야 한다. 더보기는 Link로 프리셋 조건을 전달하고, 검색 폼은 날짜·정렬 조건을 URL 쿼리에 넣어 navigate한다. 카드는 서버 ID 기반 상세 Link로 표시한다. A-01의 선택적 onExplore/onSelectItem 콜백은 A-02에서 Router 링크로 대체했다.

- 메인 화면: `src/pages/main/MainPage.jsx`
- 카드/영역/조건 폼: `src/pages/main/components/`
- 탐색 쿼리·상세 경로 생성: `src/utils/mainNavigation.js`
- 가격·상태·날짜 표시: `src/utils/mainItemDisplay.js`
- C 경로 상수: `src/constants/routes.js`

## 통합 대기 사항

실제 C 화면은 아직 dev에 없다. 초기 임시 `/explore`, `/items/:id` 링크는 NotFound로 연결됐다. 현재는 MAIN_ROUTE_AVAILABILITY가 false이므로 더보기·상세 링크를 숨기고 조건 입력과 탐색 버튼을 비활성화한다. 조회와 가격·상태 표시는 유지한다. C가 경로를 등록하고 URL 필터를 읽도록 연결한 뒤, 해당 플래그를 활성화해야 실제 화면 전환까지 완료된다. 임시 API의 가격·상태 필드도 C 계약과 맞춰야 한다.

상세 계약과 완료/미완료 검증은 `docs/api/A-02-main-connection.md`에 기록했다. C 화면을 대신 만들거나 공통 인증/라우터를 변경하지 않았다.

## 실행 및 검증

frontend에서:

```powershell
npm ci
node --test tests/main.test.mjs tests/main-navigation.test.mjs tests/main-render.test.mjs
npm run lint
npm run build
npm run dev
```

2026-10-23: 테스트 43개, lint, build 통과. 실제 서버/브라우저 통합은 C 구현 후 확인한다.

A-01의 날짜 변경 감지와 요청 경합 방어는 유지한다. 날짜가 바뀌면 재조회하며 응답의 순서를 프런트에서 재정렬하지 않는다.

## 기존 디자인과 API 데이터 연결

26번의 API 메인은 기존 design.css 테마와 이미지형 카드 구성을 재사용한다. 사용자 승인에 따라 공통 MainLayout의 헤더·푸터도 같은 브랜드로 통합했다. 실제 목록은 useMainStore를 사용하고 SERVICES/useDesignStore/고정 날짜를 참조하지 않는다.

카드는 API의 imageUrl만 사용한다. 이미지가 없거나 로드되지 않으면 준비 중 안내를 표시한다. 매장 검색과 C 탐색·상세는 실제 연결 전까지 비활성화한다. /design 시연의 예약·결제·고정 할인 정책과 데이터는 그대로 분리되어 있다.

브라우저 검증은 Playwright/Edge와 임시 샘플 API로 수행했다. 1440px/390px에서 가로 넘침 없음, 정상 목록·빈 결과·잘못된 날짜 오류·재시도·이미지 실패 대체, /design 시연 유지까지 확인했다. 실제 C 화면과 서버 정책 통합은 여전히 대기 중이다.

현재 대화의 디자인 통합 미리보기는 http://localhost:5179/ 이며 프로세스가 실행되는 동안만 사용할 수 있다. 이 포트의 샘플 API 연결 설정은 개발 서버 프로세스에만 적용했고 소스나 env 파일에는 저장하지 않았다.
