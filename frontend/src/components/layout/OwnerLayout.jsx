import { Suspense } from 'react';
import { Link, Outlet, useLocation } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { useDesignStore } from '@/store/useDesignStore';
import Badge from '@/components/common/Badge';
import Button from '@/components/common/Button';
import OwnerSidebar from './OwnerSidebar';
import styles from './OwnerLayout.module.css';

/**
 * 사장님 센터 레이아웃: 사이드바 + 상단 매장 정보 + 본문
 */
export default function OwnerLayout() {
  const shop = useDesignStore((s) => s.shop);
  const location = useLocation();

  return (
    <div className="beauty-app">
      <div className={styles.owner}>
        <OwnerSidebar />
        <div className="b-owner-body">
          <header className={styles.ownerHeader}>
            <div className="b-flex">
              <strong>{shop.name}</strong>
              <Badge>파트너 가맹점</Badge>
            </div>
            <div className="b-flex">
              <Button as={Link} size="small" to={ROUTES.HOME}>
                ↔ 고객 모드로 전환
              </Button>
              <span className="b-account">● [테스트] 사장님 계정 (살롱 드 르네)</span>
            </div>
          </header>
          <main className={styles.ownerMain} key={location.pathname}>
            <Suspense
              fallback={
                <div className="b-empty" role="status">
                  화면을 불러오는 중입니다.
                </div>
              }
            >
              <Outlet />
            </Suspense>
          </main>
        </div>
      </div>
    </div>
  );
}
