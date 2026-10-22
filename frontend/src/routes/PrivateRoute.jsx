import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/store/useAuthStore';
import { ROUTES } from '@/constants/routes';

/**
 * 인증이 필요한 라우트를 감싸는 가드. 비로그인 시 로그인 페이지로 이동시킨다.
 * 사용: <Route element={<PrivateRoute />}> ...보호할 라우트... </Route>
 */
export default function PrivateRoute() {
  const token = useAuthStore((state) => state.token);
  const location = useLocation();

  if (!token) {
    return <Navigate to={ROUTES.LOGIN} replace state={{ from: location }} />;
  }

  return <Outlet />;
}
