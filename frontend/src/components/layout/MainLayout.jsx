import { Link, Outlet } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import styles from './MainLayout.module.css';

/**
 * 공통 레이아웃: 헤더 / 본문(Outlet) / 푸터
 */
export default function MainLayout() {
  return (
    <div className={styles.layout}>
      <header className={styles.header}>
        <Link to={ROUTES.HOME} className={styles.logo}>
          thankunnie
        </Link>
        <nav>
          <Link to={ROUTES.LOGIN}>로그인</Link>
        </nav>
      </header>
      <main className={styles.main}>
        <Outlet />
      </main>
      <footer className={styles.footer}>© thankunnie</footer>
    </div>
  );
}
