import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from '@/App';
import { configureAuth } from '@/api/axiosInstance';
import { useAuthStore } from '@/store/useAuthStore';
import '@/assets/styles/global.css';

// axiosInstance 가 store 를 직접 참조하지 않도록 인증 연동 함수를 주입한다.
configureAuth({
  getToken: () => useAuthStore.getState().token,
  // 세션을 비우면 PrivateRoute 가 로그인 페이지로 이동시킨다.
  onUnauthorized: () => useAuthStore.getState().clearSession(),
});

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
