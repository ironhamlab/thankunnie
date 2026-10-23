# 메인 기능 연결 (A-01 / A-02)

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

실제 C 화면은 아직 dev에 없다. 임시 `/explore`, `/items/:id` 링크는 현재 NotFound로 연결된다. C가 경로를 등록하고 URL 필터를 읽도록 연결해야 실제 화면 전환까지 완료된다. 임시 API의 가격·상태 필드도 C 계약과 맞춰야 한다.

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

2026-10-23: 테스트 16개, lint, build 통과. 실제 서버/브라우저 통합은 C 구현 후 확인한다.

A-01의 날짜 변경 감지와 요청 경합 방어는 유지한다. 날짜가 바뀌면 재조회하며 응답의 순서를 프런트에서 재정렬하지 않는다.
