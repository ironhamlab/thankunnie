import { Suspense } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import EmptyState from '@/components/common/EmptyState';
import Footer from './Footer';
import Header from './Header';
import HomeHeader from './HomeHeader';

const HOME_VIEW_PARAMS = ['view', 'sale', 'search'];

/**
 * 고객 화면 공통 레이아웃. 메인 홈 첫 화면만 HomeHeader를 사용한다.
 */
export default function MainLayout() {
  const location = useLocation();
  const params = new URLSearchParams(location.search);
  const isHome =
    location.pathname === ROUTES.HOME && !HOME_VIEW_PARAMS.some((key) => params.has(key));

  return (
    <div className="beauty-app">
      <div className="b-layout">
        {isHome ? <HomeHeader /> : <Header />}
        <main>
          <Suspense fallback={<EmptyState role="status">화면을 불러오는 중입니다.</EmptyState>}>
            <Outlet />
          </Suspense>
        </main>
        <Footer />
      </div>
    </div>
  );
}
