import { Link, NavLink, Outlet } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import searchIcon from '@/assets/figma/8-3-15.svg';
import '@/pages/design/design.css';
import styles from './MainLayout.module.css';

/** 기존 디자인 테마를 재사용하는 API 화면 공통 레이아웃. */
export default function MainLayout() {
  return (
    <div className={`beauty-app b-layout ${styles.layout}`}>
      <header className="b-header">
        <div className="b-container b-nav">
          <Link to={ROUTES.HOME} className="b-brand">
            땡잡은 언니 <span>뷰티 타임세일</span>
          </Link>
          <div className={`b-search ${styles.search}`} role="search" aria-label="매장 검색">
            <input
              aria-label="지역, 시술 종류, 매장 검색"
              placeholder="지역, 시술 종류, 매장 검색"
              disabled
              title="검색 기능 준비 중"
            />
            <button type="button" aria-label="검색 기능 준비 중" disabled>
              <img src={searchIcon} alt="" aria-hidden="true" />
            </button>
          </div>
          <nav aria-label="고객 메뉴">
            <NavLink to={ROUTES.HOME} end>
              빈자리 찾기
            </NavLink>
          </nav>
          <Link className="b-account" to={ROUTES.LOGIN}>
            로그인
          </Link>
        </div>
      </header>
      <main className={styles.main}>
        <Outlet />
      </main>
      <footer className="b-footer">
        <div className="b-container b-row">
          <div>
            <Link className="b-brand" to={ROUTES.HOME}>
              땡잡은 언니
            </Link>
            <p>뷰티 빈자리를 더 쉽게, 땡잡은 언니</p>
            <p>© {new Date().getFullYear()} 땡잡은 언니</p>
          </div>
          <nav className="b-footer-links" aria-label="하단 메뉴">
            <Link to={ROUTES.HOME}>빈자리 찾기</Link>
            <Link to={ROUTES.LOGIN}>로그인</Link>
          </nav>
        </div>
      </footer>
    </div>
  );
}
