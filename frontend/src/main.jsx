import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from '@/App';
import { configureAuth } from '@/api/axiosInstance';
import { useAuthStore } from '@/store/useAuthStore';
import '@/assets/styles/global.css';
import '@/assets/styles/design.css';
import '@/assets/styles/remaining-screens.css';

// 실제 로그인 전까지 개발 서버에서만 테스트 사용자(OWNER_1 등)를 Bearer 토큰으로 보낸다.
// 백엔드 local·test 프로필의 테스트 사용자 인증이 받으며, 운영 빌드에서는 쓰지 않는다.
const devTestUser = import.meta.env.DEV ? import.meta.env.VITE_TEST_USER || null : null;

// axiosInstance 가 store 를 직접 참조하지 않도록 인증 연동 함수를 주입한다.
configureAuth({
  getToken: () => useAuthStore.getState().token ?? devTestUser,
  // 세션을 비우면 PrivateRoute 가 로그인 페이지로 이동시킨다.
  onUnauthorized: () => useAuthStore.getState().clearSession(),
});

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
