# UNNIE-121 A-01 목록 응답 확장

목록 항목은 `id`, `title`, `startsAt`, `applicationClosesAt`, `shopName`, `price`, `latitude`, `longitude` 8개 키를 포함한다. `utils/mainItemResponse.js`가 타입·날짜·가격·좌표 범위를 검증하고 `store/createMainStore.js`가 API의 전체 응답을 보존한다. 정상 가격 0원과 좌표 0도 허용한다.

`applicationClosesAt`은 null을 허용한다. 미등록 좌표는 `latitude: null, longitude: null`로 반환한다. 두 좌표가 모두 유효한 항목만 지도에 표시하며, 좌표가 없는 항목도 목록에서 제외하지 않는다. `price`는 서버가 결정한 원화 판매가를 그대로 표시한다.

## 연결 지점

```jsx
import MainPage from '@/pages/main/MainPage';
import { useMainStore } from '@/store/useMainStore';

<MainPage useMainStore={useMainStore} />;
```

호출 흐름은 `MainPage → useMainStore → itemApi → axiosInstance`이다. `itemApi`는 기존 A-01 임시 계약인 `/v1/items`를 호출하며 공통 axiosInstance가 `/api` baseURL과 ApiResponse의 data 추출을 담당한다. C API의 실제 경로가 확정되면 `api/itemApi.js`에서 맞춘다.

현재 백엔드에는 C 탐색 목록 API가 없다. 이번 변경은 프런트 응답 계약과 소비 코드를 복원한 것이며 서버 DTO·쿼리 구현은 포함하지 않는다. 현재 시안 `HomePage`·탐색의 샘플 데이터와 라우트는 유지했다. C API 준비 후 호스트에서 위 컴포넌트를 연결해야 한다. 실제 C 상세 화면도 없어 시연용 문자열 ID 상세 페이지로 링크하지 않는다.

카드는 매장명·제목·시작·마감·판매가를 표시한다. 지도는 동일 응답의 latitude/longitude를 KakaoMap의 lat/lng로 전달하고 가격을 마커 라벨로 표시한다. 지도 키나 SDK가 없으면 지도 이용 불가 안내를 표시한다. 조회 실패·빈 목록은 구분하며 영역별로 재시도할 수 있다.

## 검증 명령

```powershell
node --test tests/main-response.test.mjs tests/main-render.test.mjs
npm run lint
npm run build
```

테스트는 주입한 API 응답을 사용한다. 실제 C 서버 조회 및 카카오 지도 SDK 동작은 별도 통합 검증 대상이다.

2026-10-28 검증 결과: 위 Node 테스트 35개 통과, `npm run lint` 오류·경고 0, `npm run build` 통과. 복원한 MainPage와 MainSection은 Vite SSR로 로드해 렌더링 및 지도 props를 확인했다. 현재 홈에 연결하지 않았으므로 일반 프로덕션 빌드에는 복원한 화면이 포함되지 않는다.
