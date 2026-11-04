// Seed 토큰을 먼저 불러온 뒤 프로젝트 전역 스타일을 적용한다.
// 전역 스타일은 컴포넌트의 모듈 CSS보다 먼저 불러온다. 나중에 불러오면 같은 우선순위의 전역 규칙이 모듈 규칙을 덮어쓴다.
import '@seed-design/css/base.css';
import '@/assets/styles/global.css';
import '@/assets/styles/design.css';
import '@/assets/styles/remaining-screens.css';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from '@/App';
import { configureAuth } from '@/api/axiosInstance';
import { useAuthStore } from '@/store/useAuthStore';
import { getDevTestUser } from '@/utils/devTestUser';

// axiosInstance 가 store 를 직접 참조하지 않도록 인증 연동 함수를 주입한다.
configureAuth({
  // 로그인 토큰이 없으면 개발 서버에서는 테스트 계정 전환(B-F1)에서 고른 사용자를 Bearer 토큰으로 보낸다.
  getToken: () => useAuthStore.getState().token ?? getDevTestUser(),
  // 세션을 비우면 PrivateRoute 가 로그인 페이지로 이동시킨다.
  onUnauthorized: () => useAuthStore.getState().clearSession(),
});

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
