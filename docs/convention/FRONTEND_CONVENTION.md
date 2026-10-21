# Front-end 개발 컨벤션 (React + Vite)

> 적용 범위: `frontend/` 디렉토리 전체
> 기술 스택: React 19, JavaScript (ES6+), Vite, React Router v6, Zustand, Axios
> 개발 도구: Visual Studio Code
> 상위 문서: `docs/git-guide.md` — 브랜치·커밋·PR 규칙은 해당 문서를 따르며, 본 문서는 프론트엔드 코드 작성 규칙에 집중한다. 중복된 내용이 충돌하면 상위 문서를 우선한다.

---

## 1. 프로젝트 구조

```text
frontend/
├── public/                     # 정적 파일: favicon, robots.txt
├── src/
│   ├── api/                    # Axios 인스턴스 및 도메인별 API 모듈
│   │   ├── axiosInstance.js    # baseURL, 인터셉터
│   │   ├── memberApi.js
│   │   └── orderApi.js
│   ├── assets/                 # 이미지, 폰트, 전역 스타일
│   ├── components/             # 페이지에 종속되지 않는 재사용 UI
│   │   ├── common/             # Button, Input, Modal, Spinner
│   │   └── layout/             # Header, Footer, Sidebar, MainLayout
│   ├── pages/                  # 라우트에 연결되는 페이지 컴포넌트
│   │   ├── auth/               # LoginPage.jsx, SignupPage.jsx
│   │   ├── product/
│   │   └── order/
│   ├── routes/                 # AppRouter.jsx, PrivateRoute.jsx
│   ├── store/                  # 도메인별 Zustand store
│   │   ├── useAuthStore.js
│   │   └── useCartStore.js
│   ├── hooks/                  # 커스텀 훅
│   ├── utils/                  # 공통 유틸
│   ├── constants/              # 상수, 라우트 경로, 에러 메시지 매핑
│   ├── App.jsx                 # 루트 컴포넌트: Router 조립
│   └── main.jsx                # 엔트리포인트
├── .env.example                # 환경변수 템플릿: Git 추적
├── .env.local                  # 개인 환경변수: Git 제외
├── package.json
├── package-lock.json
└── vite.config.js
```

### 1.1 배치 원칙

- `components/`에는 페이지에 종속되지 않는 재사용 컴포넌트를 둔다.
- 특정 페이지에서만 쓰는 컴포넌트는 해당 `pages/{도메인}/components/` 아래에 둔다.
- `api/axiosInstance.js`, `store/useAuthStore.js`, `routes/AppRouter.jsx`, `components/layout/`는 파트장이 초기 설계 후 커밋하며, 수정 시 파트장 리뷰를 받는다.
- 컴포넌트 파일은 대표 컴포넌트 하나를 default export한다.
- 작은 보조 컴포넌트는 같은 파일에 둘 수 있으며, 외부에서 사용할 필요가 있을 때만 named export한다.

---

## 2. 네이밍 규칙

| 대상 | 규칙 | 예시 |
|---|---|---|
| 컴포넌트 파일·컴포넌트명 | PascalCase, 파일명과 컴포넌트명 일치 | `LoginForm.jsx`, `ProductCard.jsx` |
| 페이지 컴포넌트 | `{이름}Page` 접미사 | `LoginPage.jsx`, `ProductDetailPage.jsx` |
| 레이아웃 | `{이름}Layout` 접미사 | `MainLayout.jsx` |
| 커스텀 훅 | `use` 접두사, camelCase | `useAuth.js`, `useDebounce.js` |
| 유틸·API 모듈 | camelCase | `formatDate.js`, `memberApi.js` |
| Zustand store | `use{도메인}Store`, 파일명과 export 이름 일치 | `useAuthStore.js` → `useAuthStore` |
| 상수 | UPPER_SNAKE_CASE | `MAX_CART_ITEMS`, `ROUTES.LOGIN` |
| 변수 | camelCase, 의미 있는 이름 | `productList`, `email` |
| 함수 | camelCase, 동사로 시작 | `fetchProducts`, `formatPrice` |
| boolean | `is`, `has`, `can` 접두사 | `isOpen`, `hasError`, `canSubmit` |
| 이벤트 핸들러 | `handle{대상}{이벤트}` | `handleSubmit`, `handleEmailChange` |
| 이벤트 props | `on{이벤트}` | `onClick`, `onSubmit`, `onClose` |
| CSS 클래스 | kebab-case, CSS Modules는 camelCase | `login-form`, `productCard` |
| 폴더 | 소문자 kebab-case | `pages/product-detail/` |

- 컴포넌트별 폴더를 만드는 경우에는 예외적으로 PascalCase를 허용한다.

---

## 3. 컴포넌트 작성 규칙

### 3.1 기본 형태

- 함수형 컴포넌트와 Hooks를 사용한다. 클래스 컴포넌트는 사용하지 않는다.
- JavaScript 프로젝트이므로 TypeScript 전용 타입 선언은 사용하지 않는다.
- Props는 구조분해로 받는다.
- 파일 내부 순서:
  - import
  - 파일 공통 상수
  - 컴포넌트 함수: 훅 선언 → 파생값·핸들러 → return JSX
- default export는 함수 선언에 직접 붙이거나 파일 하단에 작성한다.

```jsx
import { useState } from 'react';
import { useAuthStore } from '@/store/useAuthStore';
import Button from '@/components/common/Button';

/**
 * 사용자 로그인 입력 폼
 */
export default function LoginForm({ onSuccess }) {
  const login = useAuthStore((state) => state.login);
  const [formData, setFormData] = useState({ email: '', password: '' });
  const [errorMessage, setErrorMessage] = useState('');

  const handleChange = (event) => {
    const { name, value } = event.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setErrorMessage('');

    try {
      await login(formData);
    } catch (error) {
      setErrorMessage(error?.message ?? '로그인에 실패했습니다.');
      return;
    }

    onSuccess?.();
  };

  return (
    <form onSubmit={handleSubmit} className="login-form">
      <input
        type="email"
        name="email"
        aria-label="이메일"
        value={formData.email}
        onChange={handleChange}
        required
      />
      <input
        type="password"
        name="password"
        aria-label="비밀번호"
        value={formData.password}
        onChange={handleChange}
        required
      />
      {errorMessage && <p role="alert">{errorMessage}</p>}
      <Button type="submit">로그인</Button>
    </form>
  );
}
```

### 3.2 작성 원칙

- 컴포넌트 하나는 200줄 이내를 목표로 하고, 길어지면 하위 컴포넌트 또는 커스텀 훅 분리를 검토한다.
- Props가 5개를 초과하면 관련된 값을 이름 있는 객체 prop으로 묶거나 컴포넌트 분리를 검토한다.
- 조건부 렌더링은 `&&`, 삼항 연산자 또는 early return을 사용한다.
- 중첩 삼항 연산자는 사용하지 않는다.
- 리스트의 key는 서버 ID 등 항목을 식별하는 안정적인 값을 사용한다. 배열 index는 사용하지 않는다.
- 스타일은 CSS 파일 또는 CSS Modules로 분리한다.
- 위치·진행률 등 실행 중 계산되는 값은 필요한 경우에만 인라인 스타일을 허용한다.
- `useEffect` 의존성은 실제 사용하는 값에 맞게 선언한다. 의존성 경고를 피하려고 값을 누락하거나 `eslint-disable`로 숨기지 않는다.
- `console.log`는 디버깅 중에만 사용하고 커밋 전에 제거한다.
- 필요한 개발용 로깅은 `utils/logger.js`를 통해 개발 모드에서만 출력한다.

---

## 4. 상태 관리 (Zustand)

### 4.1 전역 vs 로컬 상태 기준

| 전역: Zustand | 로컬: useState |
|---|---|
| 로그인 사용자 정보, 인증 방식에 따라 사용하는 토큰 | 폼 입력값, 모달 열림 여부 |
| 여러 화면에서 공유하는 장바구니 | 탭 선택, 현재 페이지 번호 |
| 여러 페이지에서 공유할 필요가 있는 데이터 | 단일 컴포넌트 UI 상태 |

- 전역으로 올리기 전에 여러 페이지나 컴포넌트에서 공유할 필요가 있는지 확인한다.
- 페이지네이션 등도 공유·유지 요구가 있으면 해당 요구에 맞게 관리한다.
- 토큰 저장 위치와 유지 방식은 공통 인증 구현을 따르며, 개별 컴포넌트에서 별도로 정하지 않는다.

### 4.2 Store 규칙

- Zustand의 `create`로 도메인별 store를 작성하고 named export한다.
- 기본 `create` 방식에서는 별도의 Provider나 통합 store 등록 없이 사용한다.
- 상태와 해당 상태를 변경하는 액션을 같은 store에 정의한다.
- 컴포넌트는 store 액션을 호출하고, 비동기 액션 내부에서 `api/` 모듈을 호출한다.
- API 요청 상태는 다음 패턴을 기본으로 한다.

```js
{
  data: null,
  status: 'idle', // 'idle' | 'loading' | 'succeeded' | 'failed'
  error: null
}
```

- API 요청이 없는 store에 위 필드를 불필요하게 추가하지 않는다.
- 서로 독립적인 요청은 필요하면 상태를 나누어 관리한다.
- 비동기 액션은 요청 시작·성공·실패 시 `status`와 `error`를 직접 갱신한다.
- 오류는 `{ code, message }` 형태로 정리하고, 서버 응답이 없는 경우에도 기본 메시지를 제공한다.
- 컴포넌트에서 성공·실패에 따라 후속 처리가 필요한 액션은 성공 시 결과를 반환하고, 실패 시 오류 상태를 갱신한 뒤 오류를 던진다.
- Axios 오류를 변환하는 위치와 오류 객체 형태는 공통 인터셉터 구현에 맞춘다.
- 컴포넌트에서는 selector로 필요한 상태와 액션만 구독한다.

```js
const user = useAuthStore((state) => state.data);
const login = useAuthStore((state) => state.login);
```

- 상태 변경은 `set`을 사용한다.
- 객체와 배열은 직접 수정하지 않고 새 값으로 만들어 갱신한다.
- 기존 상태를 기준으로 갱신할 때는 `set((state) => ({ ... }))` 형태를 사용한다.
- `set`은 최상위 필드를 병합하므로 중첩 객체는 필요한 단계까지 복사한다.

---

## 5. API 통신 (Axios)

- API 요청 흐름은 `컴포넌트 → store 액션 → api 모듈 → axiosInstance`로 통일한다.
- Axios 인스턴스 생성과 Axios 직접 import는 `api/axiosInstance.js`에서 담당한다.
- 개별 컴포넌트와 API 모듈에서 별도 Axios 인스턴스나 fetch로 업무 API를 호출하지 않는다.
- baseURL은 `import.meta.env.VITE_API_BASE_URL`에서 읽는다.
  - 로컬 기본값: `http://localhost:8080/api`
  - API 모듈 경로: `/v1/members`
  - 최종 요청 경로: `http://localhost:8080/api/v1/members`
- 요청 인터셉터는 공통 인증 구현에서 관리하는 토큰이 있을 때 `Authorization: Bearer {token}`을 추가한다.
- 응답 인터셉터는 백엔드 공통 응답에서 업무 데이터인 `data`를 반환한다.
- 204 응답은 본문이 없으므로 공통 응답 포맷을 검사하지 않는다.
- HTTP 오류 또는 `success: false`는 실패로 전달하여 store 액션에서 처리할 수 있게 한다.
- 일반 업무 오류와 403 권한 부족은 자동 로그아웃 대상으로 취급하지 않는다.
- 401 등 인증 오류는 로그인 실패와 기존 인증 만료를 구분하여 공통 인증 정책에 따라 처리한다.
- 도메인별 API 모듈은 객체로 export한다.

```js
// api/memberApi.js
import axiosInstance from './axiosInstance';

export const memberApi = {
  signup: (payload) => axiosInstance.post('/v1/members', payload),
  getMe: () => axiosInstance.get('/v1/members/me'),
  updateProfile: (payload) => axiosInstance.patch('/v1/members/me', payload),
};
```

- 백엔드 ErrorCode와 화면 메시지 매핑은 `constants/errorMessages.js`에서 관리하고 `docs/api/error-codes.md`와 맞춘다.

---

## 6. 라우팅 (React Router v6)

- 라우트 경로는 `constants/routes.js`에 상수로 정의한다.
- 라우트 정의, 링크, 페이지 이동에서 해당 상수를 사용한다.

```js
export const ROUTES = {
  HOME: '/',
  LOGIN: '/login',
  PRODUCT_DETAIL: '/products/:id',
};
```

- 동적 경로는 `generatePath` 등을 사용하여 실제 값을 넣는다.

```js
generatePath(ROUTES.PRODUCT_DETAIL, { id: productId });
```

- 인증 필요 페이지는 `routes/PrivateRoute.jsx`로 감싸고 비로그인 시 `ROUTES.LOGIN`으로 이동시킨다.
- 페이지 컴포넌트는 `React.lazy`와 `Suspense`로 코드 분할한다.
- 레이아웃은 `<Outlet />` 기반 중첩 라우트로 구성한다.

---

## 7. 환경변수

- 클라이언트 코드에서 사용하는 사용자 정의 Vite 환경변수는 `VITE_` 접두사를 사용한다.
- `.env.local`은 개인 설정용이며 `.gitignore` 대상으로 한다.
- 새 환경변수 추가 시 `.env.example`에 키와 더미 값을 함께 추가한다.
- `VITE_` 환경변수는 브라우저에 공개되는 값이므로 API Secret 등 비밀 키를 넣지 않는다.

---

## 8. 코드 스타일 및 도구

- 인코딩은 UTF-8, 들여쓰기는 공백 2칸으로 한다.
- 세미콜론을 사용하며, JavaScript 문자열은 작은따옴표, JSX 속성은 큰따옴표를 사용한다.
- 줄바꿈은 저장소의 `.gitattributes`를 기준으로 LF로 통일한다.
- ESLint와 React·Hooks 관련 플러그인, Prettier는 프로젝트 devDependencies로 관리한다.
- VS Code의 저장 시 포맷을 활성화하고 프로젝트의 Prettier 설정을 사용한다.
- ESLint와 Prettier 설정 파일은 파트장이 초기 작성하며, 수정 시 파트장 리뷰를 받는다.
- 설정 파일명은 프로젝트에서 사용하는 ESLint 버전과 설정 방식에 따른다.
- import 순서:
  - React
  - 외부 라이브러리
  - 절대경로 내부 모듈: `@/...`
  - 상대경로
  - 스타일 파일
- 절대경로 alias는 `@` → `src/`로 설정한다.
- `vite.config.js`의 `resolve.alias`와 VS Code의 경로 인식 설정을 맞춘다.
- var는 사용하지 않는다. 기본은 const, 재할당이 필요할 때만 let을 사용한다.
- 비동기는 async/await를 사용하고 불필요한 then 체인은 지양한다.
- 미사용 import와 변수는 커밋 전에 제거하고 ESLint 오류·경고가 없도록 한다.

---

## 9. 스타일링

- reset, 공통 CSS 변수, 폰트 등 전역 스타일은 `assets/styles/global.css`에 작성한다.
- 컴포넌트 스타일은 같은 폴더에 `{컴포넌트명}.module.css` 또는 `{컴포넌트명}.css`로 배치한다.
- 공통으로 사용하는 색상·간격·폰트 크기는 CSS 변수로 정의한다.
  - 예: `--color-primary`, `--spacing-md`
- 반응형 브레이크포인트는 팀에서 정한 값을 CSS 미디어 쿼리에 일관되게 사용한다.
- JavaScript에서도 같은 기준이 필요하면 `constants/breakpoints.js`의 값을 CSS와 맞춘다.
- CSS 미디어 쿼리 조건에 일반 CSS 변수인 `var(...)`를 사용할 수 있다고 가정하지 않는다.
- 외부 UI 라이브러리 도입은 파트장과 합의한 뒤 적용한다.

---

## 10. Git / PR 연계 규칙 (요약)

- 브랜치:
  - 기능: `feature/fe/{이슈번호}-{기능명}`
  - 버그: `fix/fe/{이슈번호}-{결함명}`
- 커밋 scope:
  - `auth`, `product`, `cart`, `order`, `router`, `store`, `api`, `layout`, `components` 등
- 커밋 예시:
  - `feat(auth): 사용자 로그인 LoginForm 컴포넌트 구현`
  - `fix(router): 비로그인 리다이렉트 경로 수정`
- `node_modules/`, `dist/`, `.env.local`, `.vite/`는 Git에서 제외한다.
- `git pull` 후 `package.json` 또는 `package-lock.json`이 변경되면 의존성을 다시 설치한다.
- 의존성 추가·변경 시 `package.json`과 `package-lock.json`을 함께 커밋한다.
- PR 생성 전 `frontend/`에서 `npm run lint`, `npm run build`를 실행하고 통과 여부를 확인한다.

### PR 리뷰 체크리스트

- [ ] 업무 API 호출이 api 모듈과 store 액션을 경유함
- [ ] 필요한 상태와 액션만 selector로 구독함
- [ ] store 상태를 직접 수정하지 않고 set으로 갱신함
- [ ] 라우트 경로 상수를 사용함
- [ ] `console.log`, 미사용 import, 주석 처리된 폐기 코드가 없음
- [ ] Hooks 의존성이 적절하고 ESLint 오류·경고가 없음
- [ ] 새 환경변수가 `.env.example`에 반영됨
- [ ] `.env.local`, 실제 토큰, 비밀 키가 diff에 없음
- [ ] `npm run lint`, `npm run build`가 통과함
- [ ] 브라우저에서 실제 동작을 확인하고 PR의 테스트 및 검증 결과에 기록함