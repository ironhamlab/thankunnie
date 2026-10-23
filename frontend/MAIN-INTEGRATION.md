# A-01 메인 기능 연결

프리셋·API 어댑터·store·화면을 구현했다. 작업 중 추가된 React 실행 설정과 공통 axiosInstance를 사용해 `src/store/useMainStore.js`와 `src/pages/home/HomePage.jsx`에서 실제 홈에 연결했다. 기존 공통 설정은 변경하지 않았다. C 서버 구현과 탐색·상세 화면은 아직 없다. 새 라이브러리나 실행 설정은 이 기능 변경에 포함하지 않았다.

호스트 앱에서 기존 Zustand `create`와 공통 axiosInstance를 사용해 store를 한 번 생성한다. 렌더 중 생성하지 않는다.

```jsx
import { create } from 'zustand';
import axiosInstance from './src/api/axiosInstance.js';
import { createItemApi } from './src/api/itemApi.js';
import { createMainStore } from './src/store/createMainStore.js';
import MainPage from './src/pages/main/MainPage.jsx';

const useMainStore = createMainStore(create, createItemApi(axiosInstance));

// 호스트 라우터의 메인 route element에 연결한다.
// onExplore(params): 탐색 화면으로 이동하며 params를 쿼리로 전달한다.
// onSelectItem(id): 실제 도메인의 상세 화면으로 이동한다.
// 두 콜백은 호스트의 constants/routes.js를 이용해 구현한다.
// 해당 화면이 없는 현재 홈에서는 콜백을 생략해 버튼을 표시하지 않는다.
<MainPage useMainStore={useMainStore} onExplore={openSearch} onSelectItem={openDetail} />;
```

공통 axiosInstance는 `http://localhost:8080/api` 등의 baseURL을 사용하고, 백엔드 공통 응답에서 `data`를 꺼내 반환하며 HTTP 오류와 `success: false`를 reject해야 한다. 실제 도메인 경로가 결정되면 `src/api/itemApi.js`의 `/v1/items`를 C 계약에 맞춘다.

메인 화면은 진입 시 두 영역을 조회하며, 한국 날짜 변경을 1초 간격의 로컬 시계 확인 및 탭 가시성 변경으로 감지한다. 같은 날짜에는 이 확인으로 API를 반복 호출하지 않는다. 더보기 클릭 시 날짜를 새로 계산하고 page=0을 전달한다. 서버가 필터와 정렬을 수행하며 프런트는 응답 순서를 유지한다.

검증 명령(Node.js 24):

```powershell
node --test frontend/tests/main.test.mjs
```

이는 날짜 경계·프리셋·API 전달·조회 독립성·재시도·응답 경합을 검증한다. 실제 Zustand vanilla store를 사용한 테스트 8개가 통과했다. 한국 날짜 경계, 프리셋, API 전달, 독립 조회·재시도, 최신 요청 우선 처리, 정규화된 공통 오류를 검증했다. npm run lint와 npm run build도 통과했다. React 브라우저 동작, 실제 HTTP/DB 필터링은 아직 검증하지 않았다.

## dev 기준 재검증 (2026-10-22)

- 별도 worktree에서 `git pull --ff-only origin dev`를 실행했다. dev는 `c824326`으로 이미 최신이었고, A-01 브랜치에도 포함돼 있었다.
- 공통 axiosInstance의 `/api` baseURL, `/v1/items` 요청 경로 조합, ApiResponse의 data 추출, `{ code, message, status }` 오류 형식과 호환된다.
- 공통 MainLayout이 이미 main 요소를 제공하므로 MainPage의 최상위 요소를 div로 수정했다.
- README의 서비스 도메인은 뷰티샵의 취소·임박 예약이다. C의 탐색 컨트롤러·DTO·API 명세는 현재 dev에 없으므로 `/items`, `applicationClosesAt`, 24시간 조건이 실제 빈자리 계약과 일치한다고 판단할 근거는 없다. C와 예약 가능한 슬롯의 시작 시각·판매 마감 시각·상태 조건을 확정해야 한다.
- 더보기·상세 화면 연결은 해당 라우트가 생길 때 진행한다. 현재 미연결 버튼은 표시하지 않는다.
- 원래 작업 폴더의 Figma 변경은 수정하지 않았다. A-01은 `C:/UCAMP/Final/thankunnie-a01-review`에서 계속 작업할 수 있다.