import { Suspense } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { useAuthStore } from '@/store/useAuthStore';
import { useDesignStore } from '@/store/useDesignStore';
import Badge from '@/components/common/Badge';
import Button from '@/components/common/Button';
import EmptyState from '@/components/common/EmptyState';
import OwnerSidebar from './OwnerSidebar';
import styles from './OwnerLayout.module.css';

/**
 * 사장님 센터 레이아웃: 사이드바 + 상단 매장 정보 + 본문
 */
export default function OwnerLayout() {
  const shop = useDesignStore((s) => s.shop);
  const token = useAuthStore((s) => s.token);
  const clearSession = useAuthStore((s) => s.clearSession);
  const location = useLocation();
  const navigate = useNavigate();

  const handleLogout = () => {
    clearSession();
    navigate(ROUTES.HOME);
  };

  return (
    <div className="beauty-app">
      <div className={styles.root}>
        <OwnerSidebar />
        <div className="b-owner-body">
          <header className={styles.header}>
            <div className="b-flex">
              <strong>{shop.name}</strong>
              <Badge>파트너 가맹점</Badge>
            </div>
            <div className="b-flex">
              <Button as={Link} size="small" to={ROUTES.HOME}>
                ↔ 고객 모드로 전환
              </Button>
              {token && (
                <Button type="button" size="small" onClick={handleLogout}>
                  로그아웃
                </Button>
              )}
              <span className="b-account">● [테스트] 사장님 계정 (살롱 드 르네)</span>
            </div>
          </header>
          <main className={styles.main} key={location.pathname}>
            <Suspense fallback={<EmptyState role="status">화면을 불러오는 중입니다.</EmptyState>}>
              <Outlet />
            </Suspense>
          </main>
        </div>
      </div>
    </div>
  );
}
