import { Suspense } from 'react';
import { Link, Outlet } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { useLogout } from '@/hooks/useLogout';
import Button from '@/components/common/Button';
import EmptyState from '@/components/common/EmptyState';
import styles from './AdminLayout.module.css';

/**
 * 관리자 화면 레이아웃: 상단 막대 + 본문
 */
export default function AdminLayout() {
  const logout = useLogout();

  return (
    <div className="beauty-app">
      <header className={styles.header}>
        <Link to={ROUTES.ADMIN} className={styles.brand}>
          <strong>땡잡은 언니</strong>
          <small>관리자</small>
        </Link>
        <Button type="button" size="small" onClick={() => logout(ROUTES.HOME)}>
          로그아웃
        </Button>
      </header>
      <main className={styles.main}>
        <Suspense fallback={<EmptyState role="status">화면을 불러오는 중입니다.</EmptyState>}>
          <Outlet />
        </Suspense>
      </main>
    </div>
  );
}
