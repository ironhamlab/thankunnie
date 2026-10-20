# Front-end 개발 컨벤션 (React + Vite)

> 적용 범위: `frontend/` 디렉토리 전체
> 기술 스택: React 19, JavaScript (ES6+), Vite, React Router v6, Redux Toolkit, Axios
> 개발 도구: Visual Studio Code (내장 Source Control, GitHub Pull Requests, GitLens)
> 상위 문서: `docs/git-guide.md` (Git 기반 팀 협업 및 형상관리) — 브랜치/커밋/PR 규칙은 해당 문서를 따르며 본 문서는 프론트엔드 코드 작성 규칙에 집중함.

---

## 1. 프로젝트 구조

```
frontend/
├── public/                     # 정적 파일 (favicon, robots.txt)
├── src/
│   ├── api/                    # Axios 인스턴스 및 도메인별 API 모듈
│   │   ├── axiosInstance.js    # baseURL, 인터셉터 (파트장 관리)
│   │   ├── memberApi.js
│   │   └── orderApi.js
│   ├── assets/                 # 이미지, 폰트, 전역 스타일
│   ├── components/             # 재사용 가능한 UI 컴포넌트 (페이지 비종속)
│   │   ├── common/             # Button, Input, Modal, Spinner 등 원자 컴포넌트
│   │   └── layout/             # Header, Footer, Sidebar, MainLayout
│   ├── pages/                  # 라우트에 1:1 매핑되는 페이지 컴포넌트
│   │   ├── auth/               # LoginPage.jsx, SignupPage.jsx
│   │   ├── product/
│   │   └── order/
│   ├── routes/                 # React Router 라우트 정의 (AppRouter.jsx, PrivateRoute.jsx)
│   ├── store/                  # Redux Toolkit
│   │   ├── index.js            # configureStore
│   │   └── slices/             # authSlice.js, cartSlice.js
│   ├── hooks/                  # 커스텀 훅 (useAuth, useFetch)
│   ├── utils/                  # 순수 함수 유틸 (formatDate, formatPrice)
│   ├── constants/              # 상수, 라우트 경로, 에러 코드 매핑
│   ├── App.jsx                 # 루트 컴포넌트 (Provider, Router 조립)
│   └── main.jsx                # 엔트리포인트
├── .env.example                # 환경변수 템플릿 (Git 추적)
├── .env.local                  # 개인 환경변수 (Git 제외 — 절대 커밋 금지)
├── package.json
└── vite.config.js
```

### 1.1 배치 원칙
- **`components/`는 페이지에 종속되지 않는 재사용 컴포넌트만**. 특정 페이지에서만 쓰는 컴포넌트는 해당 `pages/{도메인}/components/` 하위에 둠.
- `api/axiosInstance.js`, `store/index.js`, `routes/AppRouter.jsx`, `components/layout/`는 **파트장이 초기 설계 후 커밋**하며, 수정 시 파트장 리뷰 필수.
- 하나의 파일에는 하나의 컴포넌트만 `export default`. 작은 보조 컴포넌트는 named export로 같은 파일에 두어도 됨.

---

## 2. 네이밍 규칙

| 대상 | 규칙 | 예시 |
|---|---|---|
| 컴포넌트 파일 / 컴포넌트명 | PascalCase, 파일명 = 컴포넌트명 | `LoginForm.jsx`, `ProductCard.jsx` |
| 페이지 컴포넌트 | `{이름}Page` 접미사 | `LoginPage.jsx`, `ProductDetailPage.jsx` |
| 레이아웃 | `{이름}Layout` 접미사 | `MainLayout.jsx` |
| 커스텀 훅 | `use` 접두사, camelCase | `useAuth.js`, `useDebounce.js` |
| 유틸 / API 모듈 | camelCase | `formatDate.js`, `memberApi.js` |
| Redux slice | `{도메인}Slice.js`, slice name은 도메인명 | `authSlice.js` → `name: 'auth'` |
| 상수 | UPPER_SNAKE_CASE | `MAX_CART_ITEMS`, `ROUTES.LOGIN` |
| 변수 / 함수 | camelCase, 동사로 시작 | `fetchProducts`, `isLoading` |
| boolean | `is`, `has`, `can` 접두사 | `isOpen`, `hasError`, `canSubmit` |
| 이벤트 핸들러 | `handle{대상}{이벤트}` | `handleSubmit`, `handleEmailChange` |
| 이벤트 props | `on{이벤트}` | `onClick`, `onSubmit`, `onClose` |
| CSS 클래스 | kebab-case (CSS Modules 사용 시 camelCase) | `login-form`, `product-card__title` |
| 폴더 | 소문자 kebab-case (컴포넌트 폴더는 예외적으로 PascalCase 허용) | `pages/product-detail/` |

---

## 3. 컴포넌트 작성 규칙

### 3.1 기본 형태
- **함수형 컴포넌트 + Hooks**만 사용. 클래스 컴포넌트 금지.
- `React.FC` 타입 미사용 (JS 프로젝트). Props는 구조분해로 받음.
- 파일 내부 순서: import → 상수 → 컴포넌트 함수(훅 선언 → 핸들러 → 파생값 → return JSX) → export.

```jsx
import { useState } from 'react';
import { useDispatch } from 'react-redux';
import { login } from '@/store/slices/authSlice';
import Button from '@/components/common/Button';

/**
 * 사용자 로그인 입력 폼
 */
export default function LoginForm({ onSuccess }) {
  const dispatch = useDispatch();
  const [formData, setFormData] = useState({ email: '', password: '' });
  const [errorMessage, setErrorMessage] = useState('');

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      await dispatch(login(formData)).unwrap();
      onSuccess?.();
    } catch (err) {
      setErrorMessage(err.message);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="login-form">
      {/* ... */}
      <Button type="submit">로그인</Button>
    </form>
  );
}
```

### 3.2 작성 원칙
- 컴포넌트 하나는 **200줄 이내**를 목표로 하고, 넘으면 하위 컴포넌트 또는 커스텀 훅으로 분리.
- Props는 5개를 초과하면 객체로 묶거나 컴포넌트 분리를 검토.
- 조건부 렌더링은 `&&` 또는 삼항 연산자. 중첩 삼항 금지 (별도 변수나 early return으로 풀기).
- 리스트 렌더링 `key`에 배열 index 사용 금지 (서버에서 받은 id 사용).
- 인라인 스타일 금지. 스타일은 CSS 파일 / CSS Modules로 분리.
- `useEffect` 의존성 배열을 생략하거나 ESLint 경고를 `// eslint-disable`로 무시하지 않음.
- `console.log`는 디버깅 중에만 사용하고 **커밋 전 제거**. 필요한 로깅은 `utils/logger.js`를 통해 개발 모드에서만 출력.

---

## 4. 상태 관리 (Redux Toolkit)

### 4.1 전역 vs 로컬 상태 기준
| 전역 (Redux) | 로컬 (useState) |
|---|---|
| 로그인 사용자 정보, 토큰 | 폼 입력값, 모달 열림 여부 |
| 장바구니 | 탭 선택, 페이지네이션 현재 페이지 |
| 여러 페이지에서 공유하는 캐시 데이터 | 단일 컴포넌트 UI 상태 |

전역으로 올리기 전에 "두 개 이상의 페이지에서 쓰는가?"를 먼저 확인.

### 4.2 Slice 규칙
- `createSlice`로 작성, 비동기 요청은 `createAsyncThunk` 사용. 컴포넌트에서 Axios 직접 호출 금지.
- Thunk 내부에서 `api/` 모듈 함수를 호출하고, 에러는 `rejectWithValue(error.response?.data?.error)`로 전달.
- state 구조는 `{ data, status: 'idle' | 'loading' | 'succeeded' | 'failed', error }` 패턴 통일.
- 셀렉터는 slice 파일 하단에 `export const selectUser = (state) => state.auth.user;` 형태로 정의하고, 컴포넌트에서는 셀렉터만 사용.
- 상태를 직접 변이하는 것처럼 작성 가능(Immer)하지만, state 전체를 재할당(`state = ...`)하지 않음.

---

## 5. API 통신 (Axios)

- 모든 요청은 `api/axiosInstance.js`를 통해서만 수행. `fetch` 또는 `axios` 직접 import 금지.
- `baseURL`은 `import.meta.env.VITE_API_BASE_URL`에서 읽음 (기본값: `http://localhost:8080/api`).
- 요청 인터셉터: 저장된 토큰을 `Authorization: Bearer {token}` 헤더로 주입.
- 응답 인터셉터: 백엔드 공통 응답 포맷(`{ success, data, error }`)을 풀어 `data`만 반환하고, `success: false` 또는 401/403은 공통 처리(토큰 삭제 후 로그인 페이지 이동).
- 도메인별 API 모듈은 함수 단위로 export:

```js
// api/memberApi.js
import axiosInstance from './axiosInstance';

export const memberApi = {
  signup: (payload) => axiosInstance.post('/v1/members', payload),
  getMe: () => axiosInstance.get('/v1/members/me'),
  updateProfile: (payload) => axiosInstance.patch('/v1/members/me', payload),
};
```
- 백엔드 ErrorCode(`MEMBER_001` 등)와 화면 메시지 매핑은 `constants/errorMessages.js`에서 관리하고, `docs/api/error-codes.md`와 동기화.

---

## 6. 라우팅 (React Router v6)

- 라우트 경로 문자열은 `constants/routes.js`에 상수로 정의하고 하드코딩 금지.
  ```js
  export const ROUTES = { HOME: '/', LOGIN: '/login', PRODUCT_DETAIL: '/products/:id' };
  ```
- 인증 필요 페이지는 `routes/PrivateRoute.jsx`로 감싸서 비로그인 시 `/login`으로 리다이렉트.
- 페이지 컴포넌트는 `React.lazy` + `Suspense`로 코드 분할 (초기 번들 최소화).
- 레이아웃은 `<Outlet />` 기반 중첩 라우트로 구성.

---

## 7. 환경변수

- Vite 환경변수는 반드시 `VITE_` 접두사. 그 외 접두사는 클라이언트에 노출되지 않음.
- `.env.local`은 개인 설정 전용이며 **`.gitignore` 대상**. 새 환경변수 추가 시 `.env.example`에 키와 더미 값을 함께 추가하여 커밋.
- 비밀 키(API Secret 등)는 프론트엔드 환경변수에 절대 넣지 않음 (빌드 결과물에 평문 포함됨).

---

## 8. 코드 스타일 및 도구

- 인코딩 UTF-8, 들여쓰기 **공백 2칸**, 세미콜론 사용, 문자열은 작은따옴표(JSX 속성은 큰따옴표).
- 줄바꿈은 저장소 기준 LF (`core.autocrlf true` + `.gitattributes`로 자동 처리).
- ESLint(`eslint-plugin-react`, `react-hooks`) + Prettier를 프로젝트 devDependency로 고정하고, VS Code `formatOnSave` 활성화.
  - 설정 파일(`.eslintrc.cjs`, `.prettierrc`)은 파트장이 커밋하며 개인 수정 금지.
- import 순서: React → 외부 라이브러리 → 절대경로 내부 모듈(`@/...`) → 상대경로 → 스타일 파일.
- 절대경로 alias `@` → `src/` (`vite.config.js`의 `resolve.alias` 설정).
- `var` 금지, 기본 `const`, 재할당 필요 시만 `let`.
- 비동기는 `async/await` 사용, `.then()` 체인 지양.
- 미사용 import/변수는 커밋 전 제거 (ESLint 경고 0 유지).

---

## 9. 스타일링

- 전역 스타일(`reset`, 변수, 폰트)은 `assets/styles/global.css`에만 작성.
- 컴포넌트 스타일은 같은 폴더에 `{컴포넌트명}.module.css` 또는 `{컴포넌트명}.css`로 배치.
- 색상·간격·폰트 크기는 CSS 변수(`--color-primary`, `--spacing-md`)로 정의하고 하드코딩 금지.
- 반응형 브레이크포인트는 `constants/breakpoints.js`와 CSS 변수에서 동일하게 관리.
- 외부 UI 라이브러리 도입은 파트장 승인 후 팀 전체 적용 (개인 판단으로 추가 금지).

---

## 10. Git / PR 연계 규칙 (요약)

- 브랜치: `feature/fe/{이슈번호}-{기능명}`, 버그는 `fix/fe/{이슈번호}-{결함명}`.
- 커밋 scope: `auth`, `product`, `cart`, `order`, `router`, `store`, `api`, `layout`, `components` 등.
  - 예: `feat(auth): 사용자 로그인 LoginForm 컴포넌트 구현`, `fix(router): 비로그인 리다이렉트 경로 수정`
- `node_modules/`, `dist/`, `.env.local`, `.vite/`가 Source Control에 보이면 **절대 Stage하지 않고** `.gitignore` 적용 상태를 확인.
- `git pull` 후 `package.json`이 변경되었으면 `npm install` 실행. 의존성 추가 시 `package-lock.json`을 반드시 함께 커밋.
- PR 생성 전 `npm run lint`, `npm run build` 통과 필수.
- PR 리뷰 체크 항목 (리뷰어):
  - [ ] 컴포넌트에서 Axios 직접 호출 없음 (api 모듈 + thunk 경유)
  - [ ] 라우트 경로 하드코딩 없음
  - [ ] `console.log`, 미사용 import, 주석 처리된 폐기 코드 없음
  - [ ] `useEffect` 의존성 배열 정상, ESLint 경고 0
  - [ ] 새 환경변수가 `.env.example`에 반영됨
  - [ ] `.env.local`, 토큰, 비밀 키가 diff에 없음
  - [ ] 브라우저에서 실제 동작 확인 결과가 PR 템플릿 "테스트 및 검증 결과"에 기재됨
