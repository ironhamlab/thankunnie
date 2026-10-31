import { Suspense } from 'react';
import { Outlet } from 'react-router-dom';
import EmptyState from '@/components/common/EmptyState';
import InboxToastStack from '@/components/notification/InboxToastStack';
import Footer from './Footer';
import Header from './Header';

/**
 * 고객 화면 공통 레이아웃. 모든 고객 화면이 같은 헤더·푸터를 사용한다.
 */
export default function MainLayout() {
  return (
    <div className="beauty-app">
      <div className="b-layout">
        <Header />
        <main>
          <Suspense fallback={<EmptyState role="status">화면을 불러오는 중입니다.</EmptyState>}>
            <Outlet />
          </Suspense>
        </main>
        <Footer />
        <InboxToastStack />
      </div>
    </div>
  );
}
