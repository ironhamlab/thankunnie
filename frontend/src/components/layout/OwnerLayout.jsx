import { Suspense, useEffect } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { useLogout } from '@/hooks/useLogout';
import { useAuthStore } from '@/store/useAuthStore';
import { useShopStore } from '@/store/useShopStore';
import Button from '@/components/common/Button';
import EmptyState from '@/components/common/EmptyState';
import InboxToastStack from '@/components/notification/InboxToastStack';
import OwnerInboxBell from '@/components/notification/OwnerInboxBell';
import OwnerSidebar from './OwnerSidebar';
import styles from './OwnerLayout.module.css';

/**
 * 사장님 센터 레이아웃: 사이드바 + 상단 매장 정보·알림 종 + 본문
 */
export default function OwnerLayout() {
  const shop = useShopStore((s) => s.shop);
  const loadShop = useShopStore((s) => s.loadShop);
  const token = useAuthStore((s) => s.token);
  const location = useLocation();
  const logout = useLogout();

  // 상단·사이드바의 매장 이름은 로그인한 사장님 매장으로 보여 준다
  useEffect(() => {
    if (token) {
      loadShop();
    }
  }, [token, loadShop]);

  const handleLogout = () => logout(ROUTES.HOME);

  return (
    <div className="beauty-app">
      <div className={styles.root}>
        <OwnerSidebar />
        <div className="b-owner-body">
          <header className={styles.header}>
            <strong>{shop?.name}</strong>
            <div className={`b-flex ${styles.actions}`}>
              <OwnerInboxBell />
              {token && (
                <Button type="button" size="small" onClick={handleLogout}>
                  로그아웃
                </Button>
              )}
            </div>
          </header>
          <main className={styles.main} key={location.pathname}>
            <Suspense fallback={<EmptyState role="status">화면을 불러오는 중입니다.</EmptyState>}>
              <Outlet />
            </Suspense>
          </main>
        </div>
      </div>
      <InboxToastStack />
    </div>
  );
}
