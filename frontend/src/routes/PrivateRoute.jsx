import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/store/useAuthStore';
import { ROUTES } from '@/constants/routes';
import { getDevTestUser } from '@/utils/devTestUser';

/**
 * 인증이 필요한 라우트를 감싸는 가드. 비로그인 시 로그인 페이지로 이동시킨다.
 * 개발 서버에서 테스트 계정 스위치로 고른 사용자도 로그인으로 본다 (main.jsx 의 getToken 과 같은 기준).
 * 역할 검사는 하지 않는다. 역할이 맞지 않으면 서버가 403 을 주고 각 화면이 안내한다.
 * 사용: <Route element={<PrivateRoute />}> ...보호할 라우트... </Route>
 */
export default function PrivateRoute() {
  const token = useAuthStore((state) => state.token);
  const location = useLocation();

  if (!token && !getDevTestUser()) {
    return <Navigate to={ROUTES.LOGIN} replace state={{ from: location }} />;
  }

  return <Outlet />;
}
