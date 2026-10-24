import { lazy, Suspense } from 'react';
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import AppRouter from '@/routes/AppRouter';
import { DESIGN_ROUTES } from '@/constants/designRoutes';
import { ROUTES } from '@/constants/routes';

const DesignApp = lazy(() => import('@/pages/design/DesignApp'));

/**
 * 루트 컴포넌트: Router 조립
 */
export default function App() {
  return (
    <BrowserRouter>
      <Suspense fallback={<p role="status">화면을 불러오는 중입니다.</p>}>
        <Routes>
          <Route path={ROUTES.HOME} element={<DesignApp />} />
          <Route path={`${DESIGN_ROUTES.ROOT}/*`} element={<DesignApp />} />
          <Route path="*" element={<AppRouter />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  );
}
